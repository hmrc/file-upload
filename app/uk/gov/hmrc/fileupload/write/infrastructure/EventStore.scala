/*
 * Copyright 2023 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.mongodb.WriteConcern
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import org.mongodb.scala.model._
import org.mongodb.scala.model.Filters._
import play.api.Logger
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.MongoUtils.DuplicateKey
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

object EventStore {
  type SaveResult = Either[SaveError, SaveSuccess.type]
  case object SaveSuccess
  sealed trait SaveError
  case object VersionConflictError extends SaveError
  case class NotSavedError(message: String) extends SaveError

  val saveSuccess = Right(SaveSuccess)

  type GetResult = Either[GetError, Seq[UnitOfWork]]
  case class GetError(message: String)
}

trait EventStore {

  def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Future[SaveResult]

  def unitsOfWorkForAggregate(streamId: StreamId): Future[GetResult]

  def recreate(): Unit
}

class MongoEventStore(
  mongoComponent: MongoComponent,
  metrics       : MetricRegistry,
  writeConcern  : WriteConcern = WriteConcern.MAJORITY
)(using
  ExecutionContext
) extends PlayMongoRepository[UnitOfWork](
  collectionName = "events",
  mongoComponent = mongoComponent,
  domainFormat   = UnitOfWorkSerializer.format,
  indexes        = Seq(IndexModel(Indexes.hashed("streamId"), IndexOptions().background(true)))
) with EventStore:

  // OldDataPurger cleans up old data
  override lazy val requiresTtlIndex = false

  private val logger = Logger(getClass)

  private val envelopeEventsThreshold = 1000

  private val saveTimer = metrics.timer("mongo.eventStore.write")
  private val readTimer = metrics.timer("mongo.eventStore.read")
  private val largeEnvelopeMarker = metrics.meter("mongo.largeEnvelope")

  override def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Future[SaveResult] = {
    val context = saveTimer.time()

    collection
      .withWriteConcern(writeConcern)
      .insertOne(unitOfWork)
      .toFuture()
      .map: r =>
        EventStore.saveSuccess
      .recover:
        case DuplicateKey(_) =>
          Left(VersionConflictError)
        case e =>
          Left(NotSavedError(s"not saved: ${e.getMessage}"))
      .map: e =>
        context.stop()
        e
  }

  override def unitsOfWorkForAggregate(streamId: StreamId): Future[GetResult] =
    val context = readTimer.time()

    collection
      .find(equal("streamId", streamId.value))
      .toFuture()
      .map: l =>
        val sortByVersion = l.sortBy(_.version.value)

        val size = sortByVersion.size
        if size >= envelopeEventsThreshold then
          val elapsedNanos = context.stop()
          val elapsed = FiniteDuration(elapsedNanos, TimeUnit.NANOSECONDS)

          largeEnvelopeMarker.mark()

          if size % envelopeEventsThreshold <= 20 then
            logger.warn(s"large envelope: envelopeId=$streamId size=$size time=${elapsed.toMillis} ms")

          if size % 100 == 0 then
            logger.error(s"large envelope: envelopeId=$streamId size=$size")

        Right(sortByVersion)
      .recover:
        case e =>
          Left(GetError(e.getMessage))
      .map: e =>
        val elapsed = context.stop().nanoseconds
        if (elapsed > 10.seconds)
          logger.warn(s"unitsOfWorkForAggregate: events.find by streamId=$streamId took ${elapsed.toMillis} ms")

        e

  override def recreate(): Unit =
    Await.result(collection.drop().toFuture(), 5.seconds)

  def streamOlder(cutoff: Instant): Source[StreamId, org.apache.pekko.NotUsed] =
    Source
      .fromPublisher:
        mongoComponent.database.getCollection("events")
          .aggregate(Seq(
            Aggregates.group("$streamId", Accumulators.max("created", "$created")),
            Aggregates.`match`(Filters.lt("created", cutoff.toEpochMilli)),
            Aggregates.project(BsonDocument("_id" -> 1))
          ))
          .allowDiskUse(true)
          .map(_.get[BsonString]("_id").map(s => StreamId(s.getValue)))
      .collect { case Some(s) => s }

  def purge(streamIds: Seq[StreamId]): Future[Unit] =
    if streamIds.nonEmpty then
      collection.bulkWrite(streamIds.map(id => DeleteManyModel(equal("streamId", id.value)))).toFuture().map(_ => ())
    else
      Future.unit

end MongoEventStore
