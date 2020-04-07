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

package uk.gov.hmrc.fileupload.read.envelope

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB, DBMetaCommands, ReadPreference}
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

object Repository {

  type UpdateResult = Xor[UpdateError, UpdateSuccess.type]
  case object UpdateSuccess
  sealed trait UpdateError
  case object NewerVersionAvailable extends UpdateError
  case class NotUpdatedError(message: String) extends UpdateError

  val updateSuccess = Xor.right(UpdateSuccess)

  type DeleteResult = Xor[DeleteError, DeleteSuccess.type]
  case object DeleteSuccess
  case class DeleteError(message: String)

  val deleteSuccess = Xor.right(DeleteSuccess)

  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes-read-model", mongo, domainFormat = Envelope.envelopeFormat) {

  val duplicateKeyErrorCode = Some(11000)

  override def ensureIndexes(implicit ec:ExecutionContext) = {
    collection.indexesManager.ensure(Index(key = List("status" -> IndexType.Ascending, "destination" -> IndexType.Ascending), background = true)).map(Seq(_))
  }

  import reactivemongo.play.json.ImplicitBSONHandlers._
  import Repository._

  def update(writeConcern: WriteConcern = WriteConcern.Default)
            (envelope: Envelope, checkVersion: Boolean = true)
            (implicit ex: ExecutionContext): Future[UpdateResult] = {
    val selector = if (checkVersion) {
      Json.obj(
        _Id -> envelope._id.value,
        "version" -> Json.obj("$lte" -> envelope.version))
    } else {
      Json.obj(
        _Id -> envelope._id.value)
    }

    collection.update(selector = selector, update = envelope, writeConcern = writeConcern, upsert = true, multi = false).map { r =>
      if (r.ok) {
        updateSuccess
      } else {
        Xor.left(NotUpdatedError("No report updated"))
      }
    }.recover {
      case f: DatabaseException =>
        Xor.left(NewerVersionAvailable)
      case f: Throwable =>
        Xor.left(NotUpdatedError(f.getMessage))
    }
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    find("_id" -> id).map(_.headOption)
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[DeleteResult] = {
    remove("_id" -> id).map { r =>
      if (toBoolean(r)) {
        deleteSuccess
      } else {
        Xor.left(DeleteError("No report deleted"))
      }
    }.recover {
      case f: Throwable =>
        Xor.left(DeleteError(f.getMessage))
    }
  }

  def getByDestination(maybeDestination: Option[String])(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    val query = maybeDestination.map { d =>
      Json.obj("status" -> EnvelopeStatusClosed.name, "destination" -> d)
    } getOrElse {
      Json.obj("status" -> EnvelopeStatusClosed.name)
    }
    collection.find(query).cursor[Envelope](ReadPreference.secondaryPreferred).collect[List](-1, Cursor.FailOnError())
  }

  def getByStatus(status: List[EnvelopeStatus], inclusive: Boolean)(implicit ec: ExecutionContext): Enumerator[Envelope] = {

    import reactivemongo.play.iteratees.cursorProducer

    val operator = if (inclusive) "$in" else "$nin"
    val query = Json.obj("status" -> Json.obj(operator -> status.map(_.name)))

    collection
      .find(query)
      .cursor[Envelope](ReadPreference.secondaryPreferred)
      .enumerator()
  }

  def all()(implicit ec: ExecutionContext): Future[List[Envelope]] = {
    findAll()
  }

  def recreate()(implicit ec: ExecutionContext): Unit = {
    Await.result(collection.drop(failIfNotFound = false), 5 seconds)
    Await.result(ensureIndexes(ec), 5 seconds)
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
