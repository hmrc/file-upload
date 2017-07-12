/*
 * Copyright 2017 HM Revenue & Customs
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
import cats.data.Xor
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.streams.Streams
import play.api.libs.ws.WSClient
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, WithValidEnvelope}
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeCommand
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.fileupload.read.stats.Stats.FileFound

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import uk.gov.hmrc.fileupload.file.zip.MongoS3Compability._

class RetrieveFile(wsClient: WSClient, baseUrl: String) {
  def download(envelopeId: EnvelopeId, fileId: FileId)(implicit ec: ExecutionContext): Future[Source[ByteString, _]] = {
    val encodedFileId = implicitly[PathBindable[uk.gov.hmrc.fileupload.FileId]].unbind("fileId", fileId)
    val downloadUrl = s"$baseUrl/file-upload/download/envelopes/$envelopeId/files/$encodedFileId"
    Logger.debug(s"Downloading $downloadUrl")
    val t1 = System.nanoTime()
    val data = wsClient.url(downloadUrl).stream().map(_.body)
    data.foreach { _ =>
      val lapse = (System.nanoTime() - t1) / (1000 * 1000)
      Logger.info(s"Downloading file: url=$downloadUrl time=$lapse ms")
    }
    data
  }
}

class FileController(withBasicAuth: BasicAuth,
                     retrieveFileS3: (EnvelopeId, FileId) => Future[Source[ByteString, _]],
                     withValidEnvelope: WithValidEnvelope,
                     handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                     //Todo: remove else when mongoDB is not in use at all.
                     retrieveFileMongo: (Envelope, FileId) => Future[GetFileResult] =
                      (_,_) => Future.failed(new UnsupportedOperationException))
                    (implicit executionContext: ExecutionContext) extends Controller {

  def downloadFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { implicit request =>
    Logger.info(s"downloadFile: envelopeId=$envelopeId fileId=$fileId")

    withBasicAuth {
      withValidEnvelope(envelopeId) { envelope =>
        val maybeFile = envelope.getFileById(fileId).map(f => (f.name, f.fileRefId, f.length))
        maybeFile.map {
          case (filename, fileRefId, Some(length)) =>
            if (checkIsTheFileInS3(fileRefId)) {
              retrieveFileS3(envelopeId, fileId).map { source =>
                Ok.sendEntity(HttpEntity.Streamed(source, Some(length), Some("application/octet-stream")))
                  .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="${filename.getOrElse("data")}"""",
                    CONTENT_LENGTH -> s"$length",
                    CONTENT_TYPE -> "application/octet-stream")
              }
            } else {
              //Todo: remove if-else when mongoDB is not in use at all.
              retrieveFileMongo(envelope, fileId).map {
                case Xor.Right(FileFound(filenameI, lengthI, data)) => // I like inner
                  val byteArray = Source.fromPublisher(Streams.enumeratorToPublisher(data.map(ByteString.fromArray)))
                  Ok.sendEntity(HttpEntity.Streamed(byteArray, Some(lengthI), Some("application/octet-stream")))
                    .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="${filenameI.getOrElse("data")}"""",
                      CONTENT_LENGTH -> s"$lengthI",
                      CONTENT_TYPE -> "application/octet-stream")
                case Xor.Left(GetFileNotFoundError) =>
                  ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId")
              }
            }

          case _ => throw new Exception()
        }.getOrElse {
          Future.successful(ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId"))
        }
      }
    }
  }

}
