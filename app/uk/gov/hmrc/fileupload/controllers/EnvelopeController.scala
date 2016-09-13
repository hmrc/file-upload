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
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindError, FindServiceError}
import uk.gov.hmrc.fileupload.write.envelope.{CreateEnvelope, DeleteEnvelope, DeleteFile, EnvelopeCommand}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class EnvelopeController(nextId: () => EnvelopeId,
                         handleCommand: (EnvelopeCommand) => Future[Boolean],
                         findEnvelope: EnvelopeId => Future[Xor[FindError, Envelope]])
                        (implicit executionContext: ExecutionContext) extends BaseController {

  def create() = Action.async(EnvelopeParser) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ routes.EnvelopeController.show(id) }"

    val envelope = EnvelopeReport.toEnvelope(nextId(), request.body)

    val command = CreateEnvelope(envelope._id, envelope.callbackUrl)

    handleCommand(command).map {
      case true => Created.withHeaders(envelopeLocation(command.id))
      case false => ExceptionHandler(BAD_REQUEST, "Envelope not created")
    }.recover { case e => ExceptionHandler(e) }

//    createEnvelope(envelope).map {
//      case Xor.Left(CreateNotSuccessfulError(e)) => ExceptionHandler(BAD_REQUEST, "Envelope not stored")
//      case Xor.Left(CreateServiceError(e, m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
//      case Xor.Right(e) => Created.withHeaders(envelopeLocation(e._id))
//    }.recover { case e => ExceptionHandler(e) }
  }

  def show(id: EnvelopeId) = Action.async {

    import EnvelopeReport._

    findEnvelope(id).map {
      case Xor.Left(FindEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope $id not found")
      case Xor.Left(FindServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Right(e) => Ok(Json.toJson(EnvelopeReport.fromEnvelope(e)))
    }.recover { case e => ExceptionHandler(e) }
  }

  def delete(id: EnvelopeId) = Action.async {
    val command = DeleteEnvelope(id)

    handleCommand(command).map {
      case true => Accepted
      case false => ExceptionHandler(BAD_REQUEST, "Envelope not deleted")
    }.recover { case e => ExceptionHandler(e) }

//    deleteEnvelope(id).map {
//      case Xor.Right(e) => Accepted
//      case Xor.Left(DeleteEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope $id not found")
//      case Xor.Left(DeleteEnvelopeNotSuccessfulError) => ExceptionHandler(BAD_REQUEST, "Envelope not deleted")
//      case Xor.Left(DeleteServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
//    }.recover { case e => ExceptionHandler(e) }
  }

  def deleteFile(envelopeId: EnvelopeId, fileId: FileId) = Action.async { request =>
    val command = DeleteFile(envelopeId, fileId)

    handleCommand(command).map {
      case true => Ok
      case false => ExceptionHandler(BAD_REQUEST, "File not deleted")
    }.recover { case e => ExceptionHandler(e) }


    //    withValidEnvelope(envelopeId) { envelope =>
    //      deleteFileFromEnvelope(envelopeId, fileId).map {
    //        case Xor.Right(_) => Ok
    //        case Xor.Left(DeleteFileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $envelopeId")
    //        case Xor.Left(DeleteFileServiceError(msg)) => ExceptionHandler(INTERNAL_SERVER_ERROR, msg)
    //      }.recover { case e => ExceptionHandler(e) }
    //    }
  }

}
