/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{Json, JsError, JsSuccess, Reads, Writes}
import play.api.libs.ws.WSClient
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, FileStatusAvailable, WithValidEnvelope}
import uk.gov.hmrc.fileupload.read.routing.{ZipData, ZipRequest}
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeCommand
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class RetrieveFile(wsClient: WSClient, baseUrl: String):

  private val logger = Logger(getClass)

  def download(
    envelopeId: EnvelopeId,
    fileId    : FileId
  )(using
    ExecutionContext,
    Materializer
  ): Future[Source[ByteString, NotUsed]] =
    val encodedFileId = summon[PathBindable[uk.gov.hmrc.fileupload.FileId]].unbind("fileId", fileId)
    val downloadUrl = s"$baseUrl/internal-file-upload/download/envelopes/$envelopeId/files/$encodedFileId"
    logger.debug(s"Downloading $downloadUrl")
    val start = System.currentTimeMillis()
    wsClient.url(downloadUrl)
      .withHttpHeaders("User-Agent" -> "FU-backend")
      .stream()
      .flatMap: res =>
        if res.status != Status.OK then
          res.bodyAsSource.runFold("")(_ + _.utf8String)
            .flatMap(body => Future.failed(RuntimeException(s"Failed to download file: $body")))
        else
          Future.successful:
            res
              .bodyAsSource
              .mapMaterializedValue: _ =>
                logger.info(s"Downloading file: url=$downloadUrl time=${System.currentTimeMillis() - start} ms")
                NotUsed

  def getZipData(
    envelope: Envelope
  )(using
    ExecutionContext,
    Materializer
  ): Future[Option[ZipData]] =
    given Writes[ZipRequest] = ZipRequest.writes
    given Reads[ZipData]     = ZipData.format
    import play.api.libs.ws.writeableOf_JsValue
    wsClient
      .url(s"$baseUrl/internal-file-upload/zip/envelopes/${envelope._id}")
      .withHttpHeaders("User-Agent" -> "file-upload")
      .withBody(Json.toJson(ZipRequest(files = envelope.files.toList.flatten.map(f => f.fileId -> f.name.map(_.value)))))
      .withMethod("POST")
      .execute()
      .flatMap: res =>
        res.status match
          case Status.OK => res.json.validate[ZipData] match
                              case JsSuccess(zipData, _) => Future.successful(Some(zipData))
                              case JsError(errors)       => Future.failed(RuntimeException(s"Failed to download file: $errors"))
          case Status.GONE => Future.successful(None)
          case other       => res.bodyAsSource.runFold("")(_ + _.utf8String)
                                .flatMap(body => Future.failed(RuntimeException(s"Failed to download file. status: $other: $body")))

  def downloadZip(
    envelope: Envelope
  )(using
    ExecutionContext,
    Materializer
  ): Future[Source[ByteString, NotUsed]] =
    for
      zipData <- getZipData(envelope).flatMap(_.fold(Future.failed[ZipData](RuntimeException(s"Failed to download file: not found")))(Future.successful))
      res     <- wsClient.url(zipData.url.value).stream()
      stream  <-
                 if res.status != 200 then
                   res.bodyAsSource.runFold("")(_ + _.utf8String)
                     .flatMap(body => Future.failed(RuntimeException(s"Failed to download file: $body")))
                 else
                   Future.successful(
                     res
                       .bodyAsSource
                       .mapMaterializedValue(_ => NotUsed)
                   )
    yield stream

end RetrieveFile

@Singleton
class FileController @Inject()(
  appModule: ApplicationModule,
  cc       : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private val logger = Logger(getClass)

  val retrieveFileS3: (EnvelopeId, FileId) => Future[Source[ByteString, _]] = appModule.getFileFromS3
  val withValidEnvelope: WithValidEnvelope = appModule.withValidEnvelope
  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = appModule.envelopeCommandHandler

  def downloadFile(envelopeId: EnvelopeId, fileId: FileId) =
    Action.async:
      logger.info(s"downloadFile: envelopeId=$envelopeId fileId=$fileId")

      withValidEnvelope(envelopeId): envelope =>
        val foundFile = envelope.getFileById(fileId)
        if foundFile.map(_.status).exists(_ != FileStatusAvailable) then
          Future.successful(ExceptionHandler(NOT_FOUND, s"File with id: $fileId in envelope: $envelopeId is not ready for download."))
        else
          foundFile
            .map: f =>
              if f.length.isEmpty then
                logger.error(s"No file length detected for: envelopeId=$envelopeId fileId=$fileId. Trying to download it without length set.")

              retrieveFileS3(envelopeId, fileId).map: source =>
                Ok.streamed(source, f.length, Some("application/octet-stream"))
                  .withHeaders(Results.contentDispositionHeader(
                    inline = false,
                    name   = f.name.map(_.value).orElse(Some("data"))).toList: _*
                  )
            .getOrElse:
              Future.successful(ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId"))
