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

package uk.gov.hmrc.fileupload.controllers.transfer

import cats.data.Xor
import play.api.mvc.{Action, RequestHeader, Result}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.controllers.{AuthBasicModule, ExceptionHandler}
import uk.gov.hmrc.fileupload.file.zip.Zippy._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, OutputForTransfer}
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeNotFoundError, _}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class TransferController(getEnvelopesByDestination: Option[String] => Future[List[Envelope]],
                         handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                         zipEnvelope: EnvelopeId => Future[ZipResult])
                        (implicit executionContext: ExecutionContext) extends BaseController {

  def list() = Action.async { implicit request =>
    nonStubFunctionsWithBasicAuth{
      val maybeDestination = request.getQueryString("destination")
      getEnvelopesByDestination(maybeDestination).map { envelopes =>
        Ok(OutputForTransfer(envelopes))
      }
    }
  }

  def download(envelopeId: uk.gov.hmrc.fileupload.EnvelopeId) = Action.async { implicit request =>
    zipEnvelope(envelopeId) map {
      case Xor.Right(stream) => Ok.chunked(stream).withHeaders(
        CONTENT_TYPE -> "application/zip",
        CONTENT_DISPOSITION -> s"""attachment; filename="$envelopeId.zip""""
      )
      case Xor.Left(ZipEnvelopeNotFoundError | EnvelopeNotRoutedYet) =>
        ExceptionHandler(404, s"Envelope with id: $envelopeId not found")
      case Xor.Left(ZipProcessingError(message)) =>
        ExceptionHandler(INTERNAL_SERVER_ERROR, message)
    }
  }

  def delete(envelopeId: EnvelopeId) = Action.async { implicit request =>
    handleCommand(ArchiveEnvelope(envelopeId)).map {
      case Xor.Right(_) => Ok
      case Xor.Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $envelopeId not found")
      case Xor.Left(EnvelopeArchivedError) => ExceptionHandler(GONE, s"Envelope with id: $envelopeId already deleted")
      case Xor.Left(_) => ExceptionHandler(LOCKED, s"Envelope with id: $envelopeId locked")
    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }
  }

  def nonStubFunctionsWithBasicAuth(returnFunctions: => Future[Result])(implicit request: RequestHeader): Future[Result] = {
    AuthBasicModule.check(request.headers.get(AUTHORIZATION)) match{
      case false => Future.successful(Forbidden)
      case true => returnFunctions
    }
  }

}
