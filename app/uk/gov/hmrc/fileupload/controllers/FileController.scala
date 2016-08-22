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

package uk.gov.hmrc.fileupload.controllers

import cats.data.Xor
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.envelope.Envelope
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, JSONReadFile}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FileController(uploadBodyParser: (EnvelopeId, FileId) => BodyParser[Future[JSONReadFile]],
                     getMetadata: (EnvelopeId, FileId) => Future[GetMetadataResult],
                     retrieveFile: (EnvelopeId, FileId) => Future[GetFileResult],
                     getEnvelope: EnvelopeId => Future[Option[Envelope]],
                     uploadFile: UploadedFileInfo => Future[UpsertFileToEnvelopeResult],
                     updateMetadata: (EnvelopeId, FileId, Option[String], Option[String], Option[JsObject]) => Future[UpdateMetadataResult])
                    (implicit executionContext: ExecutionContext) extends BaseController {

  def upsertFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async(uploadBodyParser(envelopeId, fileId)) { request =>

    def fsReference(j: JsValue) = j match {
      case JsString(value) => FileId(value)
      case _ => FileId(j.toString)
    }

    request.body.flatMap { jsonReadFile =>

      val uploadedFileInfo = UploadedFileInfo(envelopeId, fileId, fsReference(jsonReadFile.id),
        jsonReadFile.length, jsonReadFile.uploadDate)

      uploadFile(uploadedFileInfo).map {
        case Xor.Right(_) => Ok
        case Xor.Left(UpsertFileUpdatingEnvelopeFailed) => ExceptionHandler(INTERNAL_SERVER_ERROR, "File not added to envelope")
        case Xor.Left(UpsertFileEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope $envelopeId not found")
        case Xor.Left(UpsertFileServiceError(msg)) => ExceptionHandler(INTERNAL_SERVER_ERROR, msg)
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def downloadFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { request =>
    retrieveFile(envelopeId, fileId).map {
      case Xor.Right(FileFound(filename, length, data)) =>
        Ok.feed(data).withHeaders(
          CONTENT_LENGTH -> s"${ length }",
          CONTENT_DISPOSITION -> s"""attachment; filename="${ filename.getOrElse("data") }""""
        )
      case Xor.Left(GetFileNotFoundError) =>
        ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId")
      case Xor.Left(GetFileEnvelopeNotFound) =>
        ExceptionHandler(NOT_FOUND, s"Envelope $envelopeId not found")
    }
  }

  def retrieveMetadata(envelopeId: EnvelopeId, fileId: FileId) = Action.async {
    getMetadata(envelopeId, fileId).map {
      case Xor.Right(f) => Ok(Json.toJson[GetFileMetadataReport](GetFileMetadataReport.fromFile(envelopeId, f)))
      case Xor.Left(GetMetadataNotFoundError) => ExceptionHandler(NOT_FOUND, s"File $fileId not found in envelope: $envelopeId")
      case Xor.Left(GetMetadataServiceError(message)) => ExceptionHandler(INTERNAL_SERVER_ERROR, message)
    }.recover { case e => ExceptionHandler(e) }
  }

  def metadata(envelopeId: EnvelopeId, fileId: FileId) = Action.async(FileMetadataParser) { request =>
    val report = request.body
    updateMetadata(envelopeId, fileId, report.name, report.contentType, report.metadata).map {
      case Xor.Right(_) => Ok.withHeaders(LOCATION -> s"${ request.host }${ routes.FileController.retrieveMetadata(envelopeId, fileId) }")
      case Xor.Left(UpdateMetadataNotSuccessfulError) => ExceptionHandler(INTERNAL_SERVER_ERROR, "metadata not updated")
      case Xor.Left(UpdateMetadataEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope $envelopeId not found")
      case Xor.Left(UpdateMetadataServiceError(message)) => ExceptionHandler(INTERNAL_SERVER_ERROR, message)
    }.recover { case e => ExceptionHandler(e) }
  }

}
