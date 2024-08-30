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

package uk.gov.hmrc.fileupload.read.envelope

import com.mongodb.{MongoException, ReadPreference}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.bson.conversions.Bson
import org.mongodb.scala.{Document, WriteConcern}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates.{set, unset}
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object Repository:
  type UpdateResult = Either[UpdateError, UpdateSuccess.type]
  case object UpdateSuccess
  sealed trait UpdateError
  case object NewerVersionAvailable extends UpdateError
  case class NotUpdatedError(message: String) extends UpdateError

  val updateSuccess = Right(UpdateSuccess)

  type DeleteResult = Either[DeleteError, DeleteSuccess.type]
  case object DeleteSuccess
  case class DeleteError(message: String)

  val deleteSuccess = Right(DeleteSuccess)

end Repository

class Repository(
  mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends PlayMongoRepository[Envelope](
  collectionName = "envelopes-read-model",
  mongoComponent = mongoComponent,
  domainFormat   = Envelope.envelopeFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("status", "destination"), IndexOptions().background(true))
                   )
):
  import Repository._

  // OldDataPurger cleans up old data
  override lazy val requiresTtlIndex = false

  def update(
    writeConcern: WriteConcern = WriteConcern.MAJORITY
  )(
    envelope: Envelope,
    checkVersion: Boolean = true
  ): Future[UpdateResult] =
    collection
      .withWriteConcern(writeConcern)
      .updateOne(
        filter =
          and(
            equal("_id", envelope._id.value),
            if checkVersion then lte("version", envelope.version.value) else Filters.empty()
          ),
        update = Document("$set" -> Codecs.toBson(envelope)),
        UpdateOptions().upsert(true)
      )
      .toFuture()
      .map: r =>
        updateSuccess
      .recover:
        case f: MongoException =>
          Left(NewerVersionAvailable)
        case f: Throwable =>
          Left(NotUpdatedError(f.getMessage))

  def get(id: EnvelopeId)(using ExecutionContext): Future[Option[Envelope]] =
    collection
      .find(filter = equal("_id", id.value))
      .toFuture()
      .map(_.headOption)

  def delete(id: EnvelopeId)(using ExecutionContext): Future[DeleteResult] =
    collection
      .deleteMany(filter = equal("_id", id.value))
      .toFuture()
      .map: r =>
        if r.wasAcknowledged() && r.getDeletedCount > 0 then
          deleteSuccess
        else
          Left(DeleteError("No report deleted"))
      .recover:
        case f: Throwable =>
          Left(DeleteError(f.getMessage))

  def getByDestination(maybeDestination: Option[String])(using ExecutionContext): Future[List[Envelope]] =
    collection
      .withReadPreference(ReadPreference.secondaryPreferred())
      .find(
        and(
          equal("status", EnvelopeStatus.EnvelopeStatusClosed.name),
          notEqual("isPushed", true),
          maybeDestination.fold(Filters.empty)(d => equal("destination", d))
        )
      )
      .toFuture()
      .map(_.toList)

  def getByStatus(status: List[EnvelopeStatus], inclusive: Boolean): Source[Envelope, NotUsed] =
    Source.fromPublisher:
      collection.find:
        if inclusive then
          in("status", status.map(_.name): _*)
        else
         nin("status", status.map(_.name): _*)

  def getByStatusDMS(status: List[EnvelopeStatus], isDMS: Boolean, onlyUnSeen: Boolean): Source[Envelope, NotUsed] =
    Source.fromPublisher:
      collection.find:
        and(
          in("status", status.map(_.name): _*),
          if isDMS      then equal("destination", "DMS") else notEqual("destination", "DMS"),
          if onlyUnSeen then exists("seen", false)       else Filters.empty
        )

  def markAsSeen(id: EnvelopeId): Future[Unit] =
    collection
      .updateOne(
        equal("_id", id.value),
        set("seen", Instant.now())
      )
      .toFuture()
      .map(_ => ())

  def clearSeen(): Future[Long] =
    collection
      .updateMany(
        and(
          equal("status", EnvelopeStatus.EnvelopeStatusClosed.name),
          exists("seen", true)
        ),
        unset("seen")
      )
      .toFuture()
      .map(_.getModifiedCount)

  def all()(using ExecutionContext): Future[List[Envelope]] =
    collection
      .find()
      .toFuture()
      .map(_.toList)

  def recreate(): Unit =
    Await.result(collection.drop().toFuture(), 5.seconds)
    Await.result(ensureIndexes(), 5.seconds)

  def purge(envelopeIds: Seq[EnvelopeId]): Future[Unit] =
    if envelopeIds.nonEmpty then
      collection.bulkWrite(envelopeIds.map(id => DeleteOneModel(equal("_id", id.value)))).toFuture().map(_ => ())
    else Future.unit

end Repository

class WithValidEnvelope(getEnvelope: EnvelopeId => Future[Option[Envelope]]):
  def apply(id: EnvelopeId)(block: Envelope => Future[Result])(using ExecutionContext): Future[Result] =
    getEnvelope(id).flatMap:
      case Some(e) => block(e) // eventually do other checks here, e.g. is envelope sealed?
      case None    => Future.successful:
                        Results.NotFound(Json.obj("message" -> s"Envelope with id: $id not found"))
