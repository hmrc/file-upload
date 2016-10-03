/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes-read-model", mongo, domainFormat = Envelope.envelopeFormat) {

  def update(envelope: Envelope)(implicit ex: ExecutionContext): Future[Option[Envelope]] = {
    val selector = Json.obj(
      _Id -> envelope._id.value,
      "version" -> Json.obj("$lte" -> envelope.version))
    val result = collection.findAndUpdate(selector, envelope, fetchNewObject = true, upsert = envelope.version.value == 1)

    result.map(_.value.map(Json.fromJson[Envelope](_).get))
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    find("_id" -> id).map(_.headOption)
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove("_id" -> id) map toBoolean
  }

  def getByDestination(maybeDestination: Option[String])(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    maybeDestination.map { d =>
      find("destination" -> d, "status" -> EnvelopeStatusClosed.name)
    } getOrElse {
      find("status" -> EnvelopeStatusClosed.name)
    }
  }

  def all()(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    findAll()
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
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
