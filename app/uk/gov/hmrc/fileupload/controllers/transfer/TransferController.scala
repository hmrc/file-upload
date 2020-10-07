/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.scaladsl.Source
import javax.inject.{Inject, Singleton}
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.streams.IterateeStreams
import play.api.mvc.{ControllerComponents, Results}
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId}
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler
import uk.gov.hmrc.fileupload.file.zip.Zippy._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, OutputForTransfer}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  val withBasicAuth: BasicAuth = appModule.withBasicAuth
  val getEnvelopesByDestination: Option[String] => Future[List[Envelope]] = appModule.getEnvelopesByDestination
  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = appModule.envelopeCommandHandler
  val zipEnvelope: EnvelopeId => Future[ZipResult] = appModule.zipEnvelope

  def list() = Action.async { implicit request =>
    withBasicAuth {
      val maybeDestination = request.getQueryString("destination")
      getEnvelopesByDestination(maybeDestination).map { envelopes =>
        Ok(OutputForTransfer(envelopes))
      }
    }
  }

  def download(envelopeId: uk.gov.hmrc.fileupload.EnvelopeId) = Action.async {
    zipEnvelope(envelopeId) map {
      case Right(stream) =>
        val keepOnlyNonEmptyArrays = Enumeratee.filter[Array[Byte]] { _.length > 0 }
        val source = Source.fromPublisher(IterateeStreams.enumeratorToPublisher(stream.through(keepOnlyNonEmptyArrays)))
        Ok.chunked(source).as("application/zip")
          .withHeaders(Results.contentDispositionHeader(inline = false, name = Some(s"$envelopeId.zip")).toList: _*)
      case Left(ZipEnvelopeNotFoundError | EnvelopeNotRoutedYet) =>
        ExceptionHandler(404, s"Envelope with id: $envelopeId not found")
      case Left(ZipProcessingError(message)) =>
        ExceptionHandler(INTERNAL_SERVER_ERROR, message)
    }
  }

  def delete(envelopeId: EnvelopeId) = Action.async {
    handleCommand(ArchiveEnvelope(envelopeId)).map {
      case Right(_) => Ok
      case Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $envelopeId not found")
      case Left(EnvelopeArchivedError) => ExceptionHandler(GONE, s"Envelope with id: $envelopeId already deleted")
      case Left(_) => ExceptionHandler(LOCKED, s"Envelope with id: $envelopeId locked")
    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }
  }

}
