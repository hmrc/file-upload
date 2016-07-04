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
import uk.gov.hmrc.fileupload.models.{Envelope, Sealed}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import play.api.http.Status._

object EnvelopeFacade {
  
  type CreateResult = Xor[CreateError, Envelope]
  type FindResult = Xor[FindError, Envelope]
  type DeleteResult = Xor[DeleteError, Envelope]
  type SealResult = Xor[SealError, Envelope]

	sealed trait EnvelopeError extends Exception{
		def code: Int
		def message: String
	}


	sealed trait CreateError
  case class CreateNotSuccessfulError(envelope: Envelope) extends CreateError
  case class CreateServiceError(envelope: Envelope, message: String) extends CreateError

  sealed trait FindError
  case class FindEnvelopeNotFoundError(id: String) extends FindError
  case class FindServiceError(id: String, message: String) extends FindError

	abstract class DeleteError(override val code: Int, override val message: String) extends EnvelopeError

	object DeleteError{
		def unapply(error: DeleteError) : Option[(Int, String)] = Some(error.code, error.message)
	}

  case class DeleteEnvelopeNotFoundError(msg: String) extends DeleteError(NOT_FOUND, msg)
  case class DeleteEnvelopeSealedError(msg: String) extends DeleteError(BAD_REQUEST, msg)
  case class DeleteEnvelopeNotSuccessfulError(msg: String) extends DeleteError(BAD_REQUEST,msg)
  case class DeleteServiceError(msg: String) extends DeleteError(INTERNAL_SERVER_ERROR, msg)

  sealed trait SealError
  case class SealEnvelopeNotFoundError(id: String) extends SealError
  case class SealEnvelopeAlreadySealedError(envelope: Envelope) extends SealError
  case class SealEnvelopNotSuccessfulError(envelope: Envelope) extends SealError
  case class SealServiceError(id: String, message: String) extends SealError

  def create(add: Envelope => Future[Boolean])(envelope: Envelope)(implicit ex: ExecutionContext): Future[CreateResult] =
    add(envelope).map {
      case true => Xor.right(envelope)
      case _ => Xor.left(CreateNotSuccessfulError(envelope))
    }.recover { case e => Xor.left(CreateServiceError(envelope, e.getMessage)) }

  def find(get: String => Future[Option[Envelope]])(id: String)(implicit ex: ExecutionContext): Future[FindResult] =
    get(id).map {
      case Some(e) => Xor.right(e)
      case _ => Xor.left(FindEnvelopeNotFoundError(id))
    }.recover { case e =>  Xor.left(FindServiceError(id, e.getMessage))  }

  def delete(delete: String => Future[Boolean], find: String => Future[FindResult])(id: String)(implicit ex: ExecutionContext): Future[DeleteResult] =
    find(id).flatMap {
      case Xor.Right(envelope) if envelope.isSealed() => Future { Xor.left(DeleteEnvelopeSealedError(s"Envelope ${envelope._id} sealed")) }
      case Xor.Right(envelope) => delete(envelope._id).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(DeleteEnvelopeNotSuccessfulError("Envelope not deleted"))
      }.recover { case e =>  Xor.left(DeleteServiceError(e.getMessage))  }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(DeleteEnvelopeNotFoundError(s"Envelope $i not found")) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(DeleteServiceError(m)) }
      }.recover { case e =>  Xor.left(DeleteServiceError(e.getMessage)) }

  def seal(seal: Envelope => Future[Boolean], find: String => Future[FindResult])(id: String)(implicit ex: ExecutionContext): Future[SealResult] =
    find(id).flatMap {
      case Xor.Right(envelope) if envelope.isSealed() => Future { Xor.left(SealEnvelopeAlreadySealedError(envelope)) }
      case Xor.Right(envelope) => seal(envelope.copy(status = Sealed)).map {
        case true => Xor.right(envelope)
        case _ => Xor.left(SealEnvelopNotSuccessfulError(envelope))
      }.recover{ case e =>  Xor.left(SealServiceError(id, e.getMessage)) }
      case Xor.Left(FindEnvelopeNotFoundError(i)) => Future { Xor.left(SealEnvelopeNotFoundError(i)) }
      case Xor.Left(FindServiceError(i, m)) => Future { Xor.left(SealServiceError(i, m)) }
      }.recover { case e =>  Xor.left(SealServiceError(id, e.getMessage))  }
}
