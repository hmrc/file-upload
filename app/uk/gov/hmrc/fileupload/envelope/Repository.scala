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

package uk.gov.hmrc.fileupload.envelope

import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc._
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.envelope.Service.UploadedFileInfo
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes", mongo, domainFormat = Envelope.envelopeFormat) {

  def update(envelope: Envelope)(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.update(Json.obj(_Id -> envelope._id.value), envelope).map(toBoolean)
  }

  def updateFileStatus(envelopeId: EnvelopeId, fileId: FileId, fileStatus: FileStatus)
                      (implicit ec: ExecutionContext): Future[Boolean] = {
    val selector = Json.obj(_Id -> envelopeId.value, "files.fileId" -> fileId.value)
    val update = Json.obj("$set" -> Json.obj("files.$.status" -> fileStatus.name))
    collection.update(selector, update).map(toBoolean)
  }

  // explanation: http://stackoverflow.com/questions/23470658/mongodb-upsert-sub-document
  def upsertFile(envelopeId: EnvelopeId, uploadedFileInfo: UploadedFileInfo)(implicit ex: ExecutionContext): Future[Boolean] = {
    updateFile(envelopeId, uploadedFileInfo).flatMap {
      case true => Future.successful(true)
      case false => addFile(envelopeId, uploadedFileInfo)
    }
  }

  def updateFile(envelopeId: EnvelopeId, uploadedFileInfo: UploadedFileInfo)(implicit ex: ExecutionContext): Future[Boolean] = {
    val selector = Json.obj(_Id -> envelopeId.value, "files.fileId" -> uploadedFileInfo.fileId)
    val update = Json.obj("$set" -> Json.obj(
      "files.$.length" -> uploadedFileInfo.length,
      "files.$.fsReference" -> uploadedFileInfo.fsReference,
      "files.$.uploadDate" -> uploadedFileInfo.uploadDate.map(new DateTime(_))
    ))
    collection.update(selector, update).map { _.nModified == 1 }
  }

  def addFile(envelopeId: EnvelopeId, uploadedFileInfo: UploadedFileInfo)(implicit ex: ExecutionContext): Future[Boolean] = {
    val selector = Json.obj(_Id -> envelopeId)
    val add = Json.obj("$push" -> Json.obj(
      "files" -> Json.obj(
        "fileId" -> uploadedFileInfo.fileId,
        "fsReference" -> uploadedFileInfo.fsReference,
        "length" -> uploadedFileInfo.length,
        "uploadDate" -> uploadedFileInfo.uploadDate.map(new DateTime(_)),
        "rel" -> "file"
      )
    ))
    collection.update(selector, add).map(toBoolean)
  }

  def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] = {
    insert(envelope) map toBoolean
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    find("_id" -> id).map(_.headOption)
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove("_id" -> id) map toBoolean
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }
}

trait WithValidEnvelope {
  def apply(id: EnvelopeId)(block: Envelope => Future[Result])(implicit ec: ExecutionContext): Future[Result]
}

class WithValidEnvelopeImpl(repo: Repository) extends WithValidEnvelope {
  def apply(id: EnvelopeId)(block: Envelope => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
      repo.get(id).flatMap {
      case Some(e) => block(e) // eventually do other checks here, e.g. is envelope sealed?
      case None => Future.successful(
        Results.NotFound(Json.toJson(Json.obj("message" -> s"Envelope: $id not found")))
      )
    }
  }
}