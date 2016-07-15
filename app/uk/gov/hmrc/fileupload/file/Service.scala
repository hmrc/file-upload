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

package uk.gov.hmrc.fileupload.file

import cats.data.Xor
import uk.gov.hmrc.fileupload.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}
import uk.gov.hmrc.fileupload.file.Repository.RetrieveFileResult

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type GetMetadataResult = Xor[GetMetadataError, FileMetadata]
  type UpdateMetadataResult = Xor[UpdateMetadataError, FileMetadata]

  sealed trait GetMetadataError

  case class GetMetadataNotFoundError(compositeFileId: CompositeFileId) extends GetMetadataError

  case class GetMetadataServiceError(compositeFileId: CompositeFileId, message: String) extends GetMetadataError

  sealed trait UpdateMetadataError

  case class UpdateMetadataEnvelopeNotFoundError(envelopeId: String) extends UpdateMetadataError

  case class UpdateMetadataServiceError(compositeFileId: CompositeFileId, message: String) extends UpdateMetadataError

  def getMetadata(getFileMetadata: CompositeFileId => Future[Option[FileMetadata]])(compositeFileId: CompositeFileId)
                 (implicit ex: ExecutionContext): Future[GetMetadataResult] =
    getFileMetadata(compositeFileId).map {
      case Some(e) => Xor.right(e)
      case _ => Xor.left(GetMetadataNotFoundError(compositeFileId))
    }.recoverWith { case e => Future { Xor.left(GetMetadataServiceError(compositeFileId, e.getMessage)) } }

  def updateMetadata(updateMetadata: FileMetadata => Future[Boolean], findEnvelope: String => Future[FindResult])(metadata: FileMetadata)
                    (implicit ex: ExecutionContext): Future[UpdateMetadataResult] =
    findEnvelope(metadata._id.envelopeId).flatMap {
      case Xor.Right(envelope) => updateMetadata(metadata).map {
        case true => Xor.right(metadata)
        case _ => Xor.left(UpdateMetadataServiceError(metadata._id, "Update failed"))
      }.recoverWith { case e => Future { Xor.left(UpdateMetadataServiceError(metadata._id, e.getMessage)) } }
      case Xor.Left(FindEnvelopeNotFoundError(id)) => Future { Xor.left(UpdateMetadataEnvelopeNotFoundError(id)) }
      case Xor.Left(FindServiceError(id, m)) => Future { Xor.left(UpdateMetadataServiceError(metadata._id, m)) }
    }.recoverWith { case e => Future { Xor.left(UpdateMetadataServiceError(metadata._id, e.getMessage)) } }

  def retrieveFile(fromRepository: CompositeFileId => Future[RetrieveFileResult])(compositeFileId: CompositeFileId)
                  (implicit ex: ExecutionContext): Future[RetrieveFileResult] =
    fromRepository(compositeFileId)
}
