/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.fileupload.EnvelopeId

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import com.mongodb.{MongoException, ReadPreference}
import org.bson.conversions.Bson
import org.mongodb.scala.{Document, WriteConcern}
import org.mongodb.scala.model._
import org.mongodb.scala.model.Filters._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

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

  def apply(mongoComponent: MongoComponent)(implicit ec: ExecutionContext): Repository = new Repository(mongoComponent)
}

class Repository(mongoComponent: MongoComponent)
                (implicit ec: ExecutionContext)
  extends PlayMongoRepository[Envelope](
    collectionName = "envelopes-read-model",
    mongoComponent = mongoComponent,
    domainFormat = Envelope.envelopeFormat,
    indexes = Seq(
      IndexModel(Indexes.ascending("status", "destination"), IndexOptions().background(true))
    )) {

  import Repository._

  def update(writeConcern: WriteConcern = WriteConcern.MAJORITY)
            (envelope: Envelope, checkVersion: Boolean = true): Future[UpdateResult] = {
    val selector = if (checkVersion) {
      and(
        equal("_id", envelope._id.value),
        lte("version", envelope.version.value)
      )
    } else {
      equal("_id", envelope._id.value)
    }

    val document = Document("$set" -> Codecs.toBson(envelope))

    collection
      .withWriteConcern(writeConcern)
      .updateOne(filter = selector, update = document, UpdateOptions().upsert(true))
      .toFuture()
      .map { r =>
        if (r.wasAcknowledged()) {
          updateSuccess
        } else {
          Left(NotUpdatedError("No report updated"))
        }
      }.recover {
        case f: MongoException =>
          Left(NewerVersionAvailable)
        case f: Throwable =>
          Left(NotUpdatedError(f.getMessage))
      }
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    val value = id.value
    val bsonfilter = Filters.equal("_id", value)
    collection.find(filter = bsonfilter).toFuture.map { result =>
      result.headOption
    }
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[DeleteResult] =
    collection.deleteMany(filter = Filters.equal("_id", id.value)).toFuture.map { r =>
      if (r.wasAcknowledged() && r.getDeletedCount > 0) {
        deleteSuccess
      } else {
        Left(DeleteError("No report deleted"))
      }
    }.recover {
      case f: Throwable =>
        Left(DeleteError(f.getMessage))
    }

  def getByDestination(maybeDestination: Option[String])(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    val filters: List[Bson] = List(
      equal("status", EnvelopeStatusClosed.name),
      notEqual("isPushed", true)
    ) ++ maybeDestination.map { d =>
      equal("destination", d)
    }

    collection
      .withReadPreference(ReadPreference.secondaryPreferred())
      .find(and(filters: _*))
      .toFuture.map(_.toList)
  }

  def getByStatus(status: List[EnvelopeStatus], inclusive: Boolean): Source[Envelope, akka.NotUsed] = {
    val operator = if (inclusive) in("status", status.map(_.name): _*) else nin("status", status.map(_.name): _*)
    Source.fromPublisher(collection.find(operator))
  }

  def all()(implicit ec: ExecutionContext): Future[List[Envelope]] =
    collection.find().toFuture.map(_.toList)

  def recreate(): Unit = {
    Await.result(collection.drop().toFuture(), 5 seconds)
    Await.result(ensureIndexes, 5 seconds)
  }

}

class WithValidEnvelope(getEnvelope: EnvelopeId => Future[Option[Envelope]]) {
  def apply(id: EnvelopeId)(block: Envelope => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    getEnvelope(id).flatMap {
      case Some(e) => block(e) // eventually do other checks here, e.g. is envelope sealed?
      case None => Future.successful(
        Results.NotFound(Json.obj("message" -> s"Envelope with id: $id not found"))
      )
    }
  }
}
