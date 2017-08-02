/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.Xor
import com.codahale.metrics.MetricRegistry
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.WriteConcern
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, DBMetaCommands}
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

  val duplicateKeyErrroCode = Some(11000)

  override def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Future[SaveResult] = {
    val timer = metrics.timer("MongoEventStoreSave").time()
    collection.insert(unitOfWork, writeConcern).map { r =>
      if (r.ok) {
        EventStore.saveSuccess
      } else {
        Xor.left(NotSavedError("not saved"))
      }
    }.recover {
      case e: DatabaseException if e.code == duplicateKeyErrroCode =>
        Xor.Left(VersionConflictError)
      case e =>
        Xor.left(NotSavedError(e.getMessage))
    }.map { e =>
      timer.stop()
      e
    }
  }


  override def unitsOfWorkForAggregate(streamId: StreamId): Future[GetResult] = {
    val timer = metrics.timer("MongoEventStoreRead").time()
    collection.find(BSONDocument("streamId" -> streamId.value)).cursor[UnitOfWork]().collect[List]().map { l =>
      val sortByVersion = l.sortBy(_.version.value)
      Xor.right(sortByVersion)
    }.recover { case e => Xor.left(GetError(e.getMessage)) }.map { e =>
      timer.stop()
      e
    }
  }

  override def recreate(): Unit = {
    Await.result(collection.drop(), 5 seconds)
    Await.result(ensureIndex(), 5 seconds)
  }
}
