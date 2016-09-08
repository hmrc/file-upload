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
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.utils.JsonUtils._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes-read-model", mongo, domainFormat = Envelope.envelopeFormat) {

  def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] = {
    insert(envelope) map toBoolean
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    find("_id" -> id).map(_.headOption)
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove("_id" -> id) map toBoolean
  }

  def upsertFileMetadata(envelopeId: EnvelopeId, file: File)(implicit ex: ExecutionContext): Future[Boolean] = {
    updateFileMetadata(envelopeId, file).flatMap {
      case true => Future.successful(true)
      case false => addMetadataToAFile(envelopeId, file)
    }
  }

  private def updateFileMetadata(envelopeId: EnvelopeId, file: File)(implicit ex: ExecutionContext): Future[Boolean] = {
    import file._
    if (name.nonEmpty || contentType.nonEmpty || metadata.nonEmpty) {
      val selector = Json.obj(_Id -> envelopeId, "files.fileId" -> fileId)
      val update = Json.obj("$set" -> (
       Json.obj("files.$.fileReferenceId" -> fileReferenceId) ++
        optional("files.$.name", name) ++
          optional("files.$.contentType", contentType) ++
          optional("files.$.metadata", metadata)
        ))
      collection.update(selector, update).map { _.nModified == 1 }
    } else {
      Future.successful(true) // nothing to update
    }
  }

  private def addMetadataToAFile(envelopeId: EnvelopeId, file: File)(implicit ex: ExecutionContext): Future[Boolean] = {
    import file._
    val selector = Json.obj(_Id -> envelopeId)
    val add = Json.obj("$push" -> Json.obj(
      "files" -> (
        Json.obj("fileId" -> fileId) ++
          Json.obj("fileReferenceId" -> fileReferenceId) ++
          optional("name", name) ++
          optional("contentType", contentType) ++
          optional("metadata", metadata)
        )
    )
    )
    collection.update(selector, add).map(toBoolean)
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }
}
