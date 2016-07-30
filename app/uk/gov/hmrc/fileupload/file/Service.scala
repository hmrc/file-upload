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
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.envelope.{Envelope, File}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type GetMetadataResult = Xor[GetMetadataError, File]
  sealed trait GetMetadataError
  object GetMetadataNotFoundError extends GetMetadataError
  case class GetMetadataServiceError(message: String) extends GetMetadataError

  type GetFileResult = GetFileError Xor FileFoundResult
  case class FileFoundResult(filename: Option[String] = None, length: Long = 0, data: Enumerator[Array[Byte]] = null)
  sealed trait GetFileError
  object GetFileNotFoundError extends GetFileError
  object GetFileEnvelopeNotFound extends GetFileError

  def getMetadata(getEnvelope: EnvelopeId => Future[Option[Envelope]])(envelopeId: EnvelopeId, fileId: FileId)
                 (implicit ex: ExecutionContext): Future[GetMetadataResult] =
    getEnvelope(envelopeId).map {
      case Some(e) =>
        val maybeFile = e.getFileById(fileId)
        maybeFile.map(f => Xor.right(f)).getOrElse(Xor.left(GetMetadataNotFoundError))
      case _ => Xor.left(GetMetadataNotFoundError)
    }.recoverWith { case e => Future { Xor.left(GetMetadataServiceError(e.getMessage)) } }

  def retrieveFile(getEnvelope: EnvelopeId => Future[Option[Envelope]], getFileFromRepo: FileId => Future[Option[FileFoundResult]])
                  (envelopeId: EnvelopeId, fileId: FileId)
                  (implicit ex: ExecutionContext): Future[GetFileResult] = {
    getEnvelope(envelopeId).flatMap {
      case Some(envelope) =>
        val maybeFsReference = envelope.getFileById(fileId).flatMap(_.fsReference)
        maybeFsReference match {
          case Some(id) => getFileFromRepo(id).map { maybeResult =>
            Xor.fromOption(maybeResult, ifNone = GetFileNotFoundError)
          }
          case None => Future.successful(Xor.left(GetFileNotFoundError))
        }
      case None => Future.successful(Xor.left(GetFileEnvelopeNotFound))
    }
  }

}
