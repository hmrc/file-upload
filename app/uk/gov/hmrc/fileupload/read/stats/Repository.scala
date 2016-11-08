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

package uk.gov.hmrc.fileupload.read.stats

import cats.data.Xor
import play.api.Logger
import play.api.libs.json.{Format, Json}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object InProgressFile {
  implicit val format: Format[InProgressFile] = Json.format[InProgressFile]
}

case class InProgressFile(_id: FileRefId, envelopeId: EnvelopeId, fileId: FileId, startedAt: Long)

object Repository {
  type UpdateResult = Xor[UpdateError, UpdateSuccess.type]
  case object UpdateSuccess
  sealed trait UpdateError
  case object NewerVersionAvailable extends UpdateError
  case class NotUpdatedError(message: String) extends UpdateError

  val updateSuccess = Xor.right(UpdateSuccess)

  type DeleteResultForRef = Xor[DeleteErrorForRef, DeleteSuccess.type]
  case object DeleteSuccess
  case class DeleteErrorForRef(message: String)

  val deleteSuccess = Xor.right(DeleteSuccess)
  def apply(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext)
  extends ReactiveRepository[InProgressFile, BSONObjectID](collectionName = "inprogress-files", mongo, domainFormat = InProgressFile.format) {

  import Repository._

  def delete(envelopeId: EnvelopeId, fileId: FileId): Future[Boolean] =
    remove("envelopeId" -> envelopeId, "fileId" -> fileId) map toBoolean

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }

  def all() = findAll()

  def deleteByFileRefId(fileRefId: FileRefId)(implicit ec: ExecutionContext): Future[DeleteResultForRef] = {
    remove("_id" -> fileRefId).map { r =>
      if (toBoolean(r)) {
        Logger.debug(message = s"delete ref $fileRefId")
        deleteSuccess
      } else {
        Logger.debug(message = s"Failed to delete from database ref $fileRefId")
        Xor.left(DeleteErrorForRef("No report deleted"))
      }
    }.recover {
      case f: Throwable =>
        Xor.left(DeleteErrorForRef(f.getMessage))
    }
  }
}