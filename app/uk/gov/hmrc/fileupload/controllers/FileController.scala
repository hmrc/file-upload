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

import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.fileupload.file.{CompositeFileId, FileMetadata}
import uk.gov.hmrc.play.microservice.controller.BaseController
import cats.data.Xor
import uk.gov.hmrc.fileupload.JSONReadFile
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.file.Repository.RetrieveFileResult
import uk.gov.hmrc.fileupload.file.Service._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FileController(uploadBodyParser: CompositeFileId => BodyParser[Future[JSONReadFile]],
                     addFileToEnvelope: (String, String) => Future[AddFileResult],
                     getMetadata: CompositeFileId => Future[GetMetadataResult],
                     updateMetadata: FileMetadata => Future[UpdateMetadataResult],
                     retrieveFile: CompositeFileId => Future[RetrieveFileResult])
                    (implicit executionContext: ExecutionContext) extends BaseController {

  def upload(envelopeId: String, fileId: String) = Action.async(uploadBodyParser(CompositeFileId(envelopeId, fileId))) { request =>
    //TODO: rollback file upload if file not added to envelope
	  request.body.flatMap { _ =>
		  addFileToEnvelope(envelopeId, fileId).map {
			  case Xor.Right(e) => Ok
			  case Xor.Left(AddFileEnvelopeNotFoundError(id)) => ExceptionHandler(NOT_FOUND, s"Envelope $id not found")
			  case Xor.Left(AddFileSeaeldError(e)) => ExceptionHandler(BAD_REQUEST, s"The envelope ${e._id} is sealed")
			  case Xor.Left(AddFileNotSuccessfulError(e)) => ExceptionHandler(INTERNAL_SERVER_ERROR, "File not added to envelope")
			  case Xor.Left(AddFileServiceError(id, m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
		  }.recover { case e => ExceptionHandler(e) }
	  }
  }

  def get(envelopeId: String, fileId: String) = Action.async {
    getMetadata(CompositeFileId(envelopeId, fileId)).map {
      case Xor.Right(m) => Ok(Json.toJson[GetFileMetadataReport](GetFileMetadataReport.fromFileMetadata(m)))
      case Xor.Left(GetMetadataNotFoundError(e)) => ExceptionHandler(NOT_FOUND, s"File $fileId not found")
      case Xor.Left(GetMetadataServiceError(e, m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
  }

	def metadata(envelopeId: String, fileId: String) = Action.async(FileMetadataParser) { request =>
    updateMetadata(UpdateFileMetadataReport.toFileMetadata(envelopeId, fileId, request.body)).map {
      case Xor.Right(m) => Ok
      case Xor.Left(UpdateMetadataNotFoundError(e)) => ExceptionHandler(NOT_FOUND, s"File $fileId not found")
      case Xor.Left(UpdateMetadataServiceError(e, m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
	}

  def download(envelopeId: String, fileId: String) = Action.async { request =>
    retrieveFile(CompositeFileId(envelopeId, fileId)) map  {
      case Xor.Right(result) =>
        Ok feed result.data withHeaders(
          CONTENT_LENGTH -> s"${result.length}", CONTENT_DISPOSITION -> s"""attachment; filename="${result.filename.getOrElse("data")}"""")
      case Xor.Left(result) => NotFound
    }
  }
}
