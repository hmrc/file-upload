/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type FindResult = Xor[FindError, Envelope]

  sealed trait FindError
  case object FindEnvelopeNotFoundError extends FindError
  case class FindServiceError(message: String) extends FindError

  type FindMetadataResult = Xor[FindMetadataError, File]

  sealed trait FindMetadataError
  case object FindMetadataEnvelopeNotFoundError extends FindMetadataError
  case object FindMetadataFileNotFoundError extends FindMetadataError
  case class FindMetadataServiceError(message: String) extends FindMetadataError

  def find(get: EnvelopeId => Future[Option[Envelope]])(id: EnvelopeId)
          (implicit ex: ExecutionContext): Future[FindResult] =
    get(id).map {
      case Some(e) => Xor.right(e)
      case _ => Xor.left(FindEnvelopeNotFoundError)
    }.recover { case e => Xor.left(FindServiceError(e.getMessage)) }

  def findMetadata(find: EnvelopeId => Future[FindResult])(id: EnvelopeId, fileId: FileId)
                  (implicit ex: ExecutionContext): Future[FindMetadataResult] =
    find(id).map {
      case Xor.Right(envelope) =>
        envelope.getFileById(fileId) match {
          case Some(file) => Xor.right(file)
          case None => Xor.left(FindMetadataFileNotFoundError)
        }
      case Xor.Left(FindEnvelopeNotFoundError) => Xor.left(FindMetadataEnvelopeNotFoundError)
      case Xor.Left(FindServiceError(m)) => Xor.left(FindMetadataServiceError(m))
    }.recover { case e => Xor.left(FindMetadataServiceError(e.getMessage)) }

}
