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

import akka.stream.scaladsl.Source.fromPublisher
import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.streams.Streams.enumeratorToPublisher
import play.api.mvc._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.Service._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus}
import uk.gov.hmrc.fileupload.read.stats.Stats.GetInProgressFileResult
import uk.gov.hmrc.fileupload.utils.JsonUtils.jsonBodyParser
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.envelope.{Envelope => WriteEnvelope}
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class EnvelopeController(withBasicAuth: BasicAuth,
                         envelopeDefaultConstraints: EnvelopeConstraints,
                         nextId: () => EnvelopeId,
                         handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                         findEnvelope: EnvelopeId => Future[Xor[FindError, Envelope]],
                         findMetadata: (EnvelopeId, FileId) => Future[Xor[FindMetadataError, read.envelope.File]],
                         findAllInProgressFile: () => Future[GetInProgressFileResult],
                         deleteInProgressFile: (FileRefId) => Future[Boolean],
                         getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Enumerator[Envelope])
                        (implicit executionContext: ExecutionContext) extends Controller {

  def create() = Action.async(jsonBodyParser[CreateEnvelopeRequest]) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(id) }"
    val command = CreateEnvelope(nextId(), request.body.callbackUrl, request.body.expiryDate, request.body.metadata, request.body.constraints)
    handleCreate(envelopeLocation, command)
  }

  def createWithId(id: EnvelopeId) = Action.async(jsonBodyParser[CreateEnvelopeRequest]) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(id) }"
    val command = CreateEnvelope(id, request.body.callbackUrl, request.body.expiryDate, request.body.metadata, request.body.constraints)
    handleCreate(envelopeLocation, command)
  }

  private def handleCreate(envelopeLocation: EnvelopeId => (String, String), command: CreateEnvelope): Future[Result] = {
    handleCommand(command).map {
      case Xor.Right(_) => Created.withHeaders(envelopeLocation(command.id))
      case Xor.Left(EnvelopeAlreadyCreatedError) => ExceptionHandler(BAD_REQUEST, "Envelope already created")
      case Xor.Left(EnvelopeMaxSizePerItemError) => ExceptionHandler(BAD_REQUEST, s"Max size per item is ${envelopeDefaultConstraints.maxSizePerItem}")
      case Xor.Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(EnvelopeMaxNumFilesExceededError) => ExceptionHandler(BAD_REQUEST, "Envelope max number files exceeded")
      case Xor.Left(EnvelopeMaxSizeExceededError) => ExceptionHandler(BAD_REQUEST, "Envelope Size exceeds maximum")
      case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not created")
    }.recover { case e => ExceptionHandler(e) }
  }

  def delete(id: EnvelopeId) = Action.async { implicit request =>
    withBasicAuth {
      handleCommand(DeleteEnvelope(id)).map {
        case Xor.Right(_) => Ok
        case Xor.Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
        case Xor.Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
        case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not deleted")
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def deleteFile(id: EnvelopeId, fileId: FileId) = Action.async { request =>
    handleCommand(DeleteFile(id, fileId)).map {
      case Xor.Right(_) => Ok
      case Xor.Left(FileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
      case Xor.Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
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

  def list(getEnvelopesByStatusQuery: GetEnvelopesByStatus) = Action { implicit request =>
    import EnvelopeReport._

    val enumerator = getEnvelopesByStatus(getEnvelopesByStatusQuery.status, getEnvelopesByStatusQuery.inclusive)
    Ok.chunked(fromPublisher(enumeratorToPublisher(enumerator.map(e => Json.toJson(fromEnvelope(e))))))
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

  def inProgressFiles() = Action.async {
    findAllInProgressFile().map {
      case Xor.Right(inProgressFiles) => Ok(Json.toJson(inProgressFiles))
      case Xor.Left(error) => InternalServerError("It was not possible to retrieve in progress files")
    }
  }

  def deleteInProgressFileByRefId(fileRefId: FileRefId) = Action.async {
    deleteInProgressFile(fileRefId).map {
      case true => Ok
      case false => InternalServerError("It was not possible to delete the in progress file")
    }.recover { case e => ExceptionHandler(e) }
  }
}
