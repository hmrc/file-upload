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

import cats.data.Xor
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload.events.FileUploadedAndAssigned
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

object Service {

  type CreateResult = Xor[CreateError, Envelope]
  type FindResult = Xor[FindError, Envelope]
  type DeleteResult = Xor[DeleteError, Envelope]
  type UpsertFileResult = Xor[UpsertFileError, Envelope]
  type DeleteFileResult = Xor[DeleteFileError, FileId]

  type UpdateMetadataResult = Xor[UpdateMetadataError, UpdateMetadataSuccess.type]
  case object UpdateMetadataSuccess

  sealed trait CreateError
  case class CreateNotSuccessfulError(envelope: Envelope) extends CreateError
  case class CreateServiceError(envelope: Envelope, message: String) extends CreateError

  sealed trait FindError
  case object FindEnvelopeNotFoundError extends FindError
  case class FindServiceError(message: String) extends FindError

  sealed trait DeleteError
  case object DeleteEnvelopeNotFoundError extends DeleteError
  case object DeleteEnvelopeNotSuccessfulError extends DeleteError
  case class DeleteServiceError(message: String) extends DeleteError

  sealed trait UpsertFileError
  case class UpsertFileServiceError(message: String) extends UpsertFileError
  case object UpsertFileUpdatingEnvelopeFailed extends UpsertFileError

  sealed trait UpdateMetadataError
  case object UpdateMetadataNotSuccessfulError extends UpdateMetadataError
  case class UpdateMetadataServiceError(message: String) extends UpdateMetadataError

  type UpsertFileToEnvelopeResult = UpsertFileError Xor UpsertFileSuccess.type
  case object UpsertFileSuccess

  case class UploadedFileInfo(envelopeId: EnvelopeId,
                              fileId: FileId,
                              fsReference: FileId,
                              length: Long,
                              uploadDate: Option[Long])

  object UploadedFileInfo {
    implicit val format = Json.format[UploadedFileInfo]
  }

  case class UploadedFileMetadata(envelopeId: EnvelopeId,
                                  fileId: FileId,
                                  name: Option[String],
                                  contentType: Option[String],
                                  metadata: Option[JsObject])

  sealed trait DeleteFileError
  case object DeleteFileNotFoundError extends DeleteFileError
  case class DeleteFileServiceError(message: String) extends DeleteFileError

  def create(add: Envelope => Future[Boolean])(envelope: Envelope)(implicit ex: ExecutionContext): Future[CreateResult] =
    add(envelope).map {
      case true => Xor.right(envelope)
      case _ => Xor.left(CreateNotSuccessfulError(envelope))
    }.recover { case e => Xor.left(CreateServiceError(envelope, e.getMessage)) }

  def find(get: EnvelopeId => Future[Option[Envelope]])(id: EnvelopeId)(implicit ex: ExecutionContext): Future[FindResult] =
    get(id).map {
      case Some(e) => Xor.right(e)
      case _ => Xor.left(FindEnvelopeNotFoundError)
    }.recover { case e => Xor.left(FindServiceError(e.getMessage)) }

  def delete(delete: EnvelopeId => Future[Boolean], find: EnvelopeId => Future[FindResult])(id: EnvelopeId)
            (implicit ex: ExecutionContext): Future[DeleteResult] =
    find(id).flatMap {
      case Xor.Right(envelope) => delete(id).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(DeleteEnvelopeNotSuccessfulError)
      }.recover { case e => Xor.left(DeleteServiceError(e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError) => Future { Xor.left(DeleteEnvelopeNotFoundError) }
      case Xor.Left(FindServiceError(m)) => Future { Xor.left(DeleteServiceError(m)) }
    }.recover { case e => Xor.left(DeleteServiceError(e.getMessage)) }

  def uploadFile(upsertFile: (EnvelopeId, UploadedFileInfo) => Future[Boolean], publish: AnyRef => Unit)
                (uploadedFileInfo: UploadedFileInfo)
                (implicit ex: ExecutionContext): Future[UpsertFileToEnvelopeResult] =
    upsertFile(uploadedFileInfo.envelopeId, uploadedFileInfo).map {
      case true =>
        publish(FileUploadedAndAssigned(envelopeId = uploadedFileInfo.envelopeId, fileId = uploadedFileInfo.fileId))
        Xor.right(UpsertFileSuccess)
      case false => Xor.left(UpsertFileUpdatingEnvelopeFailed)
    }.recover {
      case NonFatal(e) => Xor.left(UpsertFileServiceError(e.getMessage))
    }

  def updateMetadata(upsertFileMetadata: UploadedFileMetadata => Future[Boolean])
                    (uploadedFileMetadata: UploadedFileMetadata)
                    (implicit ex: ExecutionContext): Future[UpdateMetadataResult] =
    upsertFileMetadata(uploadedFileMetadata).map {
      case true => Xor.right(UpdateMetadataSuccess)
      case false => Xor.left(UpdateMetadataNotSuccessfulError)
    }.recover {
      case NonFatal(e) => Xor.left(UpdateMetadataServiceError(e.getMessage))
    }

  def updateFileStatus(updateFileStatus: (EnvelopeId, FileId, FileStatus) => Future[Boolean])
                      (envelopeId: EnvelopeId, fileId: FileId, status: FileStatus)
                      (implicit ex: ExecutionContext): Future[UpdateMetadataResult] =
    updateFileStatus(envelopeId, fileId, status).map {
      case true =>  Xor.right(UpdateMetadataSuccess)
      case false => Xor.left(UpdateMetadataNotSuccessfulError)
    }.recover {
      case NonFatal(e) => Xor.left(UpdateMetadataServiceError(e.getMessage))
    }

  def deleteFile(deleteFile: (EnvelopeId, FileId) => Future[Boolean])
                (envelopeId: EnvelopeId, fileId: FileId)
                (implicit ex: ExecutionContext): Future[DeleteFileResult] =
    deleteFile(envelopeId, fileId).map {
      case true => Xor.right(fileId)
      case false => Xor.left(DeleteFileNotFoundError)
    }.recover { case NonFatal(e) => Xor.left(DeleteFileServiceError(e.getMessage))}
}
