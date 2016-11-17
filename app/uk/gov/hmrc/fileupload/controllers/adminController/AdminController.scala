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

package uk.gov.hmrc.fileupload.controllers.adminController

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeReport, ExceptionHandler, GetEnvelopesByStatus}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus}
import uk.gov.hmrc.fileupload.read.stats.Stats._
import uk.gov.hmrc.fileupload.write.envelope.{CreateEnvelope, EnvelopeAlreadyCreatedError, EnvelopeCommand, EventSerializer}
import uk.gov.hmrc.fileupload.write.infrastructure.{EventSerializer => _, _}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class AdminController (nextId: () => EnvelopeId,
                       handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                       findAllInProgressFile: () => Future[GetInProgressFileResult],
                       getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Enumerator[Envelope])
                      (implicit executionContext: ExecutionContext) extends BaseController {

  implicit val eventWrites = EventSerializer.eventWrite

  def list(getEnvelopesByStatusQuery: GetEnvelopesByStatus) = Action { implicit request =>
    import EnvelopeReport._

    Ok.chunked(
      getEnvelopesByStatus(getEnvelopesByStatusQuery.status, getEnvelopesByStatusQuery.inclusive).map(e => Json.toJson(fromEnvelope(e))))
  }

  def inProgressFiles() = Action.async {
    findAllInProgressFile().map {
      case Xor.Right(inProgressFiles) => Ok(Json.toJson(inProgressFiles))
      case Xor.Left(error) => InternalServerError("It was not possible to retrieve in progress files")
    }
  }

  private def handleCreate(envelopeLocation: EnvelopeId => (String, String), command: CreateEnvelope): Future[Result] = {
    handleCommand(command).map {
      case Xor.Right(_) => Created.withHeaders(envelopeLocation(command.id))
      case Xor.Left(EnvelopeAlreadyCreatedError) => ExceptionHandler(BAD_REQUEST, "Envelope already created")
      case Xor.Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Xor.Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not created")
    }.recover { case e => ExceptionHandler(e) }
  }
}
