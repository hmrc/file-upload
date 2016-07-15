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

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type CreateResult = Xor[CreateError, Envelope]
  type FindResult = Xor[FindError, Envelope]
  type DeleteResult = Xor[DeleteError, Envelope]
  type SealResult = Xor[SealError, Envelope]
  type AddFileResult = Xor[AddFileError, Envelope]

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

  sealed trait SealError
  case class SealEnvelopeNotFoundError(id: String) extends SealError
  case class SealEnvelopeAlreadySealedError(envelope: Envelope) extends SealError
  case class SealEnvelopNotSuccessfulError(envelope: Envelope) extends SealError
  case class SealServiceError(id: String, message: String) extends SealError

  sealed trait AddFileError
  case class AddFileEnvelopeNotFoundError(id: String) extends AddFileError
  case class AddFileSeaeldError(envelope: Envelope) extends AddFileError
  case class AddFileNotSuccessfulError(envelope: Envelope) extends AddFileError
  case class AddFileServiceError(id: String, message: String) extends AddFileError

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
      case Xor.Right(envelope) if envelope.isSealed => Future { Xor.left(DeleteEnvelopeSealedError(envelope)) }
      case Xor.Right(envelope) => delete(envelope._id).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(DeleteEnvelopeNotSuccessfulError(envelope))
      }.recover { case e => Xor.left(DeleteServiceError(id, e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(DeleteEnvelopeNotFoundError(i)) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(DeleteServiceError(i, m)) }
    }.recover { case e => Xor.left(DeleteServiceError(id, e.getMessage)) }

  def seal(seal: Envelope => Future[Boolean], find: String => Future[FindResult])(id: String)(implicit ex: ExecutionContext): Future[SealResult] =
    find(id).flatMap {
      case Xor.Right(envelope) if envelope.isSealed => Future { Xor.left(SealEnvelopeAlreadySealedError(envelope)) }
      case Xor.Right(envelope) => seal(envelope.copy(status = Sealed)).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(SealEnvelopNotSuccessfulError(envelope))
      }.recover { case e => Xor.left(SealServiceError(id, e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(SealEnvelopeNotFoundError(i)) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(SealServiceError(i, m)) }
    }.recover { case e => Xor.left(SealServiceError(id, e.getMessage)) }

  def addFile(toHref: (String, String) => String, update: Envelope => Future[Boolean], find: String => Future[FindResult])
             (envelopeId: String, fileId: String)
             (implicit ex: ExecutionContext): Future[AddFileResult] =
    find(envelopeId).flatMap {
      case Xor.Right(envelope) if envelope.isSealed => Future { Xor.left(AddFileSeaeldError(envelope)) }
      case Xor.Right(envelope) => {
        val updatedEnvelope = addFileToEnvelope(toHref, envelope, fileId)
        update(updatedEnvelope).map {
          case true => Xor.right(updatedEnvelope)
          case _ => Xor.left(AddFileNotSuccessfulError(envelope))
        }
      }.recover { case e => Xor.left(AddFileServiceError(envelopeId, e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(AddFileEnvelopeNotFoundError(i)) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(AddFileServiceError(i, m)) }
    }.recover { case e => Xor.left(AddFileServiceError(envelopeId, e.getMessage)) }

  private def addFileToEnvelope(toHref: (String, String) => String, envelope: Envelope, fileId: String): Envelope = {
    val newFile: Seq[File] = Seq(File(href = toHref(envelope._id, fileId), id = fileId))

    envelope.files match {
      case None => envelope.copy(files = Some(newFile))
      case Some(seq) => envelope.copy(files = Some(seq ++ newFile))
    }
  }
}
