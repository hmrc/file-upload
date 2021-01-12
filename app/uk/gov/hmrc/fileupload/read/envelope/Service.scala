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

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type FindResult = Either[FindError, Envelope]

  sealed trait FindError
  case object FindEnvelopeNotFoundError extends FindError
  case class FindServiceError(message: String) extends FindError

  type FindMetadataResult = Either[FindMetadataError, File]

  sealed trait FindMetadataError
  case object FindMetadataEnvelopeNotFoundError extends FindMetadataError
  case object FindMetadataFileNotFoundError extends FindMetadataError
  case class FindMetadataServiceError(message: String) extends FindMetadataError

  def find(get: EnvelopeId => Future[Option[Envelope]])(id: EnvelopeId)
          (implicit ex: ExecutionContext): Future[FindResult] =
    get(id).map {
      case Some(e) => Right(e)
      case _ => Left(FindEnvelopeNotFoundError)
    }.recover { case e => Left(FindServiceError(e.getMessage)) }

  def findMetadata(find: EnvelopeId => Future[FindResult])(id: EnvelopeId, fileId: FileId)
                  (implicit ex: ExecutionContext): Future[FindMetadataResult] =
    find(id).map {
      case Right(envelope) =>
        envelope.getFileById(fileId) match {
          case Some(file) => Right(file)
          case None => Left(FindMetadataFileNotFoundError)
        }
      case Left(FindEnvelopeNotFoundError) => Left(FindMetadataEnvelopeNotFoundError)
      case Left(FindServiceError(m)) => Left(FindMetadataServiceError(m))
    }.recover { case e => Left(FindMetadataServiceError(e.getMessage)) }

}
