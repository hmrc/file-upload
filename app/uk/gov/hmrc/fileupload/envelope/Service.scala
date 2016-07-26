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
import play.api.libs.json.JsObject

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

object Service {

  type CreateResult = Xor[CreateError, Envelope]
  type FindResult = Xor[FindError, Envelope]
  type DeleteResult = Xor[DeleteError, Envelope]
  type UpsertFileResult = Xor[UpsertFileError, Envelope]

  type UpdateMetadataResult = Xor[UpdateMetadataError, UpdateMetadataSuccess.type]
  object UpdateMetadataSuccess

  sealed trait CreateError
  case class CreateNotSuccessfulError(envelope: Envelope) extends CreateError
  case class CreateServiceError(envelope: Envelope, message: String) extends CreateError

  sealed trait FindError
  case class FindEnvelopeNotFoundError(id: String) extends FindError
  case class FindServiceError(id: String, message: String) extends FindError

  sealed trait DeleteError
  case class DeleteEnvelopeNotFoundError(id: String) extends DeleteError
  case class DeleteEnvelopeSealedError(envelope: Envelope) extends DeleteError
  case class DeleteEnvelopeNotSuccessfulError(envelope: Envelope) extends DeleteError
  case class DeleteServiceError(id: String, message: String) extends DeleteError

  sealed trait UpsertFileError
  case class UpsertFileEnvelopeNotFoundError(id: String) extends UpsertFileError
  case class UpsertFileNotSuccessfulError(envelope: Envelope) extends UpsertFileError
  case class UpsertFileServiceError(message: String) extends UpsertFileError
  object UpsertFileUpdatingEnvelopeFailed extends UpsertFileError

  sealed trait UpdateMetadataError
  object UpdateMetadataEnvelopeNotFoundError extends UpdateMetadataError
  object UpdateMetadataNotSuccessfulError extends UpdateMetadataError
  case class UpdateMetadataServiceError(message: String) extends UpdateMetadataError

  type UpsertFileToEnvelopeResult = UpsertFileError Xor UpsertFileSuccess.type
  object UpsertFileSuccess

  case class UploadedFileInfo(envelopeId: String,
                              fileId: String,
                              fsReference: String,
                              length: Long,
                              uploadDate: Option[Long])

  def create(add: Envelope => Future[Boolean])(envelope: Envelope)(implicit ex: ExecutionContext): Future[CreateResult] =
    add(envelope).map {
      case true => Xor.right(envelope)
      case _ => Xor.left(CreateNotSuccessfulError(envelope))
    }.recover { case e => Xor.left(CreateServiceError(envelope, e.getMessage)) }

  def find(get: String => Future[Option[Envelope]])(id: String)(implicit ex: ExecutionContext): Future[FindResult] =
    get(id).map {
      case Some(e) => Xor.right(e)
      case _ => Xor.left(FindEnvelopeNotFoundError(id))
    }.recover { case e => Xor.left(FindServiceError(id, e.getMessage)) }

  def delete(delete: String => Future[Boolean], find: String => Future[FindResult])(id: String)(implicit ex: ExecutionContext): Future[DeleteResult] =
    find(id).flatMap {
      case Xor.Right(envelope) => delete(envelope._id).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(DeleteEnvelopeNotSuccessfulError(envelope))
      }.recover { case e => Xor.left(DeleteServiceError(id, e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(DeleteEnvelopeNotFoundError(i)) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(DeleteServiceError(i, m)) }
    }.recover { case e => Xor.left(DeleteServiceError(id, e.getMessage)) }

  def uploadFile(getEnvelope: String => Future[Option[Envelope]], updateEnvelope: Envelope => Future[Boolean])
                (uploadedFileInfo: UploadedFileInfo)
                (implicit ex: ExecutionContext): Future[UpsertFileToEnvelopeResult] = {
    getEnvelope(uploadedFileInfo.envelopeId).flatMap({
      case Some(envelope) =>
        val updatedEnvelope = envelope.addFile(uploadedFileInfo)
        updateEnvelope(updatedEnvelope).map{
          case true => Xor.right(UpsertFileSuccess)
          case false => Xor.left(UpsertFileUpdatingEnvelopeFailed)
        }
      case None => Future.successful(Xor.left(UpsertFileEnvelopeNotFoundError(uploadedFileInfo.envelopeId)))
    }).recover { case NonFatal(e) => Xor.left(UpsertFileServiceError(e.getMessage)) }
  }

  def updateMetadata(getEnvelope: String => Future[Option[Envelope]], updateEnvelope: Envelope => Future[Boolean])
                    (envelopeId: String, fileId: String, name: Option[String], contentType: Option[String], metadata: Option[JsObject])
                    (implicit ex: ExecutionContext): Future[UpdateMetadataResult] = {
    getEnvelope(envelopeId).flatMap {
      case Some(envelope) =>
        val updatedEnvelope = envelope.addMetadata(fileId = fileId, name = name, contentType = contentType, metadata = metadata)
        updateEnvelope(updatedEnvelope).map {
          case true => Xor.right(UpdateMetadataSuccess)
          case false => Xor.left(UpdateMetadataNotSuccessfulError)
        }
      case None => Future.successful(Xor.left(UpdateMetadataEnvelopeNotFoundError))
    }.recover { case NonFatal(e) => Xor.left(UpdateMetadataServiceError(e.getMessage))}
  }

}
