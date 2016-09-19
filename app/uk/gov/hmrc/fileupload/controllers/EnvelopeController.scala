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
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.read.envelope.Envelope
import uk.gov.hmrc.fileupload.read.envelope.Service._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class EnvelopeController(nextId: () => EnvelopeId,
                         handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                         findEnvelope: EnvelopeId => Future[Xor[FindError, Envelope]],
                         findMetadata: (EnvelopeId, FileId) => Future[Xor[FindMetadataError, read.envelope.File]])
                        (implicit executionContext: ExecutionContext) extends BaseController {

  def create() = Action.async(EnvelopeParser) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ routes.EnvelopeController.show(id) }"

    val command = CreateEnvelope(nextId(), request.body.callbackUrl)

    handleCommand(command).map {
      case Xor.Right(_) => Created.withHeaders(envelopeLocation(command.id))
      case Xor.Left(EnvelopeCommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not created")
    }.recover { case e => ExceptionHandler(e) }
  }

  def delete(id: EnvelopeId) = Action.async {
    handleCommand(DeleteEnvelope(id)).map {
      case Xor.Right(_) => Accepted
      case Xor.Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
      case Xor.Left(EnvelopeCommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not deleted")
    }.recover { case e => ExceptionHandler(e) }
  }

  def deleteFile(id: EnvelopeId, fileId: FileId) = Action.async { request =>
    handleCommand(DeleteFile(id, fileId)).map {
      case Xor.Right(_) => Ok
      case Xor.Left(FileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
      case Xor.Left(EnvelopeCommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "File not deleted")
    }.recover { case e => ExceptionHandler(e) }
  }

  def show(id: EnvelopeId) = Action.async {
    import EnvelopeReport._

    findEnvelope(id).map {
      case Xor.Right(e) => Ok(Json.toJson(fromEnvelope(e)))
      case Xor.Left(FindEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
      case Xor.Left(FindServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
  }

  def retrieveMetadata(id: EnvelopeId, fileId: FileId) = Action.async { request =>
    import GetFileMetadataReport._

    findMetadata(id, fileId).map {
      case Xor.Right(f) => Ok(Json.toJson(fromFile(id, f)))
      case Xor.Left(FindMetadataEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
      case Xor.Left(FindMetadataFileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
      case Xor.Left(FindMetadataServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
  }
}
