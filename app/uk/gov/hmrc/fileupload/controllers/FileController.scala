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
import play.api.libs.json.{JsString, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.envelope.{Envelope, WithValidEnvelope}
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, JSONReadFile}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FileController(uploadBodyParser: (EnvelopeId, FileId) => BodyParser[Future[JSONReadFile]],
                     retrieveFile: (Envelope, FileId) => Future[GetFileResult],
                     withValidEnvelope: WithValidEnvelope,
                     uploadFile: UploadedFileInfo => Future[UpsertFileToEnvelopeResult],
                     upsertFileMetadata: UploadedFileMetadata => Future[UpdateMetadataResult])
                    (implicit executionContext: ExecutionContext) extends BaseController {


  def deleteFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { request =>
    withValidEnvelope(envelopeId) { envelope =>
      Future.successful(Ok)
    }
  }

  def upsertFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async(uploadBodyParser(envelopeId, fileId)) { request =>
    withValidEnvelope(envelopeId) { envelope =>
      request.body.flatMap { jsonReadFile =>
        val uploadedFileInfo = UploadedFileInfo(envelopeId, fileId, FileId(jsonReadFile.id.asInstanceOf[JsString].value),
          jsonReadFile.length, jsonReadFile.uploadDate)

        uploadFile(uploadedFileInfo).map {
          case Xor.Right(_) => Ok
          case Xor.Left(UpsertFileUpdatingEnvelopeFailed) => ExceptionHandler(INTERNAL_SERVER_ERROR, "File not added to envelope")
          case Xor.Left(UpsertFileServiceError(msg)) => ExceptionHandler(INTERNAL_SERVER_ERROR, msg)
        }.recover { case e => ExceptionHandler(e) }
      }
    }
  }

  def downloadFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { request =>
    withValidEnvelope(envelopeId) { envelope =>
      retrieveFile(envelope, fileId).map {
        case Xor.Right(FileFound(filename, length, data)) =>
          Ok.feed(data).withHeaders(
            CONTENT_LENGTH -> s"${ length }",
            CONTENT_DISPOSITION -> s"""attachment; filename="${ filename.getOrElse("data") }""""
          )
        case Xor.Left(GetFileNotFoundError) =>
          ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId")
      }
    }
  }

  def retrieveMetadata(envelopeId: EnvelopeId, fileId: FileId) = Action.async { request =>
    withValidEnvelope(envelopeId) { envelope =>
      envelope.getFileById(fileId).map(GetFileMetadataReport.fromFile(envelopeId, _)) match {
        case Some(report) => Future.successful(Ok(Json.toJson(report)))
        case None => Future.successful(
          ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId")
        )
      }
    }.recover { case e => ExceptionHandler(e) }
  }

  def metadata(envelopeId: EnvelopeId, fileId: FileId) = Action.async(FileMetadataParser) { request =>
    withValidEnvelope(envelopeId) { envelope =>
      val report = request.body
      upsertFileMetadata(UploadedFileMetadata(envelopeId, fileId, report.name, report.contentType, report.metadata)).map {
        case Xor.Right(_) => Ok.withHeaders(LOCATION -> s"${ request.host }${ routes.FileController.retrieveMetadata(envelopeId, fileId) }")
        case Xor.Left(UpdateMetadataNotSuccessfulError) => ExceptionHandler(INTERNAL_SERVER_ERROR, "metadata not updated")
        case Xor.Left(UpdateMetadataEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope $envelopeId not found")
        case Xor.Left(UpdateMetadataServiceError(message)) => ExceptionHandler(INTERNAL_SERVER_ERROR, message)
      }.recover { case e => ExceptionHandler(e) }
    }
  }

}
