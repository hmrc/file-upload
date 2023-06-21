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

import akka.stream.scaladsl.Source
import com.mongodb.{MongoException, ReadPreference}
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

object Repository {

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

  def apply(mongoComponent: MongoComponent)(implicit ec: ExecutionContext): Repository =
    new Repository(mongoComponent)
}

class Repository(
  mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[Envelope](
  collectionName = "envelopes-read-model",
  mongoComponent = mongoComponent,
  domainFormat   = Envelope.envelopeFormat,
  indexes        = Seq(
                     IndexModel(Indexes.ascending("status", "destination"), IndexOptions().background(true))
                   )
) {
  import Repository._

  // OldDataPurger cleans up old data
  override lazy val requiresTtlIndex = false

  def update(
    writeConcern: WriteConcern = WriteConcern.MAJORITY
  )(
    envelope: Envelope,
    checkVersion: Boolean = true
  ): Future[UpdateResult] = {
    val selector =
      if (checkVersion)
        and(
          equal("_id", envelope._id.value),
          lte("version", envelope.version.value)
        )
      else
        equal("_id", envelope._id.value)

    collection
      .withWriteConcern(writeConcern)
      .updateOne(
        filter = selector,
        update = Document("$set" -> Codecs.toBson(envelope)),
        UpdateOptions().upsert(true)
      )
      .toFuture()
      .map { r =>
        if (r.wasAcknowledged())
          updateSuccess
        else
          Left(NotUpdatedError("No report updated"))
      }.recover {
        case f: MongoException =>
          Left(NewerVersionAvailable)
        case f: Throwable =>
          Left(NotUpdatedError(f.getMessage))
      }
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] =
    collection
      .find(filter = equal("_id", id.value))
      .toFuture
      .map(_.headOption)

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[DeleteResult] =
    collection
      .deleteMany(filter = equal("_id", id.value))
      .toFuture
      .map { r =>
        if (r.wasAcknowledged() && r.getDeletedCount > 0)
          deleteSuccess
        else
          Left(DeleteError("No report deleted"))
      }.recover {
        case f: Throwable =>
          Left(DeleteError(f.getMessage))
      }

  def getByDestination(maybeDestination: Option[String])(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    val filters: List[Bson] = List(
      equal("status", EnvelopeStatusClosed.name),
      notEqual("isPushed", true)
    ) ++ maybeDestination.map(d =>
      equal("destination", d)
    )

    collection
      .withReadPreference(ReadPreference.secondaryPreferred())
      .find(and(filters: _*))
      .toFuture.map(_.toList)
  }

  def getByStatus(status: List[EnvelopeStatus], inclusive: Boolean): Source[Envelope, akka.NotUsed] = {
    val operator = if (inclusive) in("status", status.map(_.name): _*) else nin("status", status.map(_.name): _*)
    Source.fromPublisher(collection.find(operator))
  }

  def getByStatusDMS(status: List[EnvelopeStatus], isDMS: Boolean, onlyUnSeen: Boolean): Source[Envelope, akka.NotUsed] = {
    val operator = and(
      in("status", status.map(_.name): _*),
      if (isDMS) equal("destination", "DMS") else notEqual("destination", "DMS"),
      if (onlyUnSeen) exists("seen", false) else BsonDocument()
    )
    Source.fromPublisher(collection.find(operator))
  }

  def markAsSeen(id: EnvelopeId): Future[Unit] =
    collection
      .updateOne(equal("_id", id.value), set("seen", Instant.now()))
      .toFuture()
      .map(_ => ())

  def clearSeen(): Future[Long] =
    collection
      .updateMany(
        and(equal("status", EnvelopeStatusClosed.name), exists("seen", true)),
        unset("seen")
      )
      .toFuture()
      .map(_.getModifiedCount)

  def all()(implicit ec: ExecutionContext): Future[List[Envelope]] =
    collection
      .find()
      .toFuture
      .map(_.toList)

  def recreate(): Unit = {
    Await.result(collection.drop().toFuture(), 5.seconds)
    Await.result(ensureIndexes, 5.seconds)
  }

  def purge(envelopeIds: Seq[EnvelopeId]): Future[Unit] =
    if (envelopeIds.nonEmpty)
      collection.bulkWrite(envelopeIds.map(id => DeleteOneModel(equal("_id", id.value)))).toFuture().map(_ => ())
    else Future.unit
}

class WithValidEnvelope(getEnvelope: EnvelopeId => Future[Option[Envelope]]) {
  def apply(id: EnvelopeId)(block: Envelope => Future[Result])(implicit ec: ExecutionContext): Future[Result] =
    getEnvelope(id).flatMap {
      case Some(e) => block(e) // eventually do other checks here, e.g. is envelope sealed?
      case None    => Future.successful(
                        Results.NotFound(Json.obj("message" -> s"Envelope with id: $id not found"))
                      )
    }
}
