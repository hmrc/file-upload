/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload.write.infrastructure

import java.util.concurrent.TimeUnit

import cats.data.Xor
import com.codahale.metrics.MetricRegistry
import play.api.Logger
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteConcern
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object EventStore {
  type SaveResult = Xor[SaveError, SaveSuccess.type]
  case object SaveSuccess
  sealed trait SaveError
  case object VersionConflictError extends SaveError
  case class NotSavedError(message: String) extends SaveError

  val saveSuccess = Xor.right(SaveSuccess)

  type GetResult = Xor[GetError, Seq[UnitOfWork]]
  case class GetError(message: String)
}

trait EventStore {

  def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Future[SaveResult]

  def unitsOfWorkForAggregate(streamId: StreamId): Future[GetResult]

  def recreate(): Unit
}

class MongoEventStore(mongo: () => DB with DBMetaCommands, metrics: MetricRegistry, writeConcern: WriteConcern = WriteConcern.Default)
                     (implicit ec: ExecutionContext,
                      reader: BSONDocumentReader[UnitOfWork],
                      writer: BSONDocumentWriter[UnitOfWork]) extends EventStore {

  val collection: BSONCollection = mongo().collection[BSONCollection]("events")

  ensureIndex()

  def ensureIndex(): Future[Boolean] =
    collection.indexesManager.ensure(Index(key = List("streamId" -> IndexType.Hashed), background = true))

  val duplicateKeyErrorCode = Some(11000)
  val envelopeEventsThreshold = 1000

  val saveTimer = metrics.timer("mongo.eventStore.write")
  val readTimer = metrics.timer("mongo.eventStore.read")
  val largeEnvelopeMarker = metrics.meter("mongo.largeEnvelope")

  override def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Future[SaveResult] = {
    val context = saveTimer.time()
    collection.insert(unitOfWork, writeConcern).map { r =>
      if (r.ok) {
        EventStore.saveSuccess
      } else {
        Xor.left(NotSavedError("not saved"))
      }
    }.recover {
      case e: DatabaseException if e.code == duplicateKeyErrorCode =>
        Xor.Left(VersionConflictError)
      case e =>
        Xor.left(NotSavedError(s"not saved: ${e.getMessage}"))
    }.map { e =>
      context.stop()
      e
    }
  }

  override def unitsOfWorkForAggregate(streamId: StreamId): Future[GetResult] = {
    val context = readTimer.time()
    collection.find(BSONDocument("streamId" -> streamId.value), None).cursor[UnitOfWork]().collect[List](-1, Cursor.FailOnError()).map { l =>
      val sortByVersion = l.sortBy(_.version.value)
      val size = sortByVersion.size
      if (size >= envelopeEventsThreshold) {
        val elapsedNanos = context.stop()
        val elapsed = FiniteDuration(elapsedNanos, TimeUnit.NANOSECONDS)

        largeEnvelopeMarker.mark()
        if (size % envelopeEventsThreshold <= 20) {
          Logger.warn(s"large envelope: envelopeId=$streamId size=$size time=${elapsed.toMillis} ms")
        }
        if (size % 100 == 0) {
          Logger.error(s"large envelope: envelopeId=$streamId size=$size")
        }
      }
      Xor.right(sortByVersion)
    }.recover { case e =>
      Xor.left(GetError(e.getMessage))
    }.map { e =>
      val elapsedNanos = context.stop()
      val elapsed = FiniteDuration(elapsedNanos, TimeUnit.NANOSECONDS)

      if (elapsed > FiniteDuration(10, TimeUnit.SECONDS)) {
        Logger.warn(s"unitsOfWorkForAggregate: events.find by streamId=$streamId took ${elapsed.toMillis} ms")
      }

      e
    }
  }

  override def recreate(): Unit = {
    Await.result(collection.drop(failIfNotFound = false), 5 seconds)
    Await.result(ensureIndex(), 5 seconds)
  }
}
