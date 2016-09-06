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

package uk.gov.hmrc.fileupload.transfer

import cats.data.Xor
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.envelope.EnvelopeStatusDeleted
import uk.gov.hmrc.fileupload.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type SoftDeleteResult = Xor[SoftDeleteError, EnvelopeId]

  sealed trait SoftDeleteError
  case class SoftDeleteServiceError(message: String) extends SoftDeleteError
  case object SoftDeleteEnvelopeNotFound extends SoftDeleteError
  case object SoftDeleteEnvelopeAlreadyDeleted extends SoftDeleteError
  case object SoftDeleteEnvelopeInWrongState extends SoftDeleteError

  def softDelete(delete: (EnvelopeId) => Future[Boolean], getEnvelope: (EnvelopeId) => Future[FindResult])
                (envelopeId: EnvelopeId)
                (implicit ex: ExecutionContext): Future[SoftDeleteResult] =
    delete(envelopeId).flatMap {
      case true => Future.successful(Xor.Right(envelopeId))
      case false => getEnvelope(envelopeId).map {
        case Xor.Right(e) => e.status match {
          case EnvelopeStatusDeleted => Xor.Left(SoftDeleteEnvelopeAlreadyDeleted)
          case _ => Xor.Left(SoftDeleteEnvelopeInWrongState)
        }
        case Xor.Left(FindEnvelopeNotFoundError) => Xor.Left(SoftDeleteEnvelopeNotFound)
        case Xor.Left(FindServiceError(m)) => Xor.Left(SoftDeleteServiceError(m))
      }.recover { case e => Xor.left(SoftDeleteServiceError(e.getMessage)) }
    }.recover { case e => Xor.left(SoftDeleteServiceError(e.getMessage)) }
}
