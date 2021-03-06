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

package uk.gov.hmrc.fileupload.controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.{FileStatusAvailable, WithValidEnvelope}
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeCommand
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class RetrieveFile(wsClient: WSClient, baseUrl: String) {

  private val logger = Logger(getClass)

  def download(envelopeId: EnvelopeId, fileId: FileId)(implicit ec: ExecutionContext): Future[Source[ByteString, _]] = {
    val encodedFileId = implicitly[PathBindable[uk.gov.hmrc.fileupload.FileId]].unbind("fileId", fileId)
    val downloadUrl = s"$baseUrl/internal-file-upload/download/envelopes/$envelopeId/files/$encodedFileId"
    logger.debug(s"Downloading $downloadUrl")
    val t1 = System.nanoTime()
    val data = wsClient.url(downloadUrl)
      .withHttpHeaders("User-Agent" -> "FU-backend")
      .stream()
      .map(_.bodyAsSource)
    data.foreach { _ =>
      val lapse = (System.nanoTime() - t1) / (1000 * 1000)
      logger.info(s"Downloading file: url=$downloadUrl time=$lapse ms")
    }
    data
  }
}

@Singleton
class FileController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  private val logger = Logger(getClass)

  val withBasicAuth: BasicAuth = appModule.withBasicAuth
  val retrieveFileS3: (EnvelopeId, FileId) => Future[Source[ByteString, _]] = appModule.getFileFromS3
  val withValidEnvelope: WithValidEnvelope = appModule.withValidEnvelope
  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = appModule.envelopeCommandHandler

  def downloadFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { implicit request =>
    logger.info(s"downloadFile: envelopeId=$envelopeId fileId=$fileId")

    withBasicAuth {
      withValidEnvelope(envelopeId) { envelope =>
        val foundFile = envelope.getFileById(fileId)
        if (foundFile.map(_.status).exists(_ != FileStatusAvailable))
          Future.successful(ExceptionHandler(NOT_FOUND, s"File with id: $fileId in envelope: $envelopeId is not ready for download."))
        else
          foundFile.map { f =>
            if (f.length.isEmpty)
              logger.error(s"No file length detected for: envelopeId=$envelopeId fileId=$fileId. Trying to download it without length set.")

            retrieveFileS3(envelopeId, fileId).map { source =>
              Ok.streamed(source, f.length, Some("application/octet-stream"))
                .withHeaders(Results.contentDispositionHeader(inline = false, name = f.name.orElse(Some("data"))).toList: _*)
            }
          }.getOrElse {
            Future.successful(ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId"))
          }
      }
    }
  }
}
