/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers.routing

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.fileupload.ApplicationModule
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler
import uk.gov.hmrc.fileupload.utils.NumberFormatting.formatAsKiloOrMegabytes
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RoutingController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  private val logger = Logger(getClass)

  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = appModule.envelopeCommandHandler
  val newId: () => String = appModule.newId

  def createRoutingRequest() = Action.async(parse.json) { implicit request =>
    withJsonBody[RouteEnvelopeRequest] { requestParams =>
      import requestParams._
      val requestId = newId()
      handleCommand(SealEnvelope(envelopeId, requestId, destination, application)).map {
        case Right(_) =>
          Created.withHeaders(LOCATION -> uk.gov.hmrc.fileupload.controllers.routing.routes.RoutingController.routingStatus(requestId).url)
        case Left(EnvelopeSealedError)
           | Left(EnvelopeRoutingAlreadyRequestedError) =>
          ExceptionHandler(BAD_REQUEST, s"Routing request already received for envelope: $envelopeId")
        case Left(FilesWithError(ids)) =>
          ExceptionHandler(BAD_REQUEST, s"Files: ${ids.mkString("[", ", ", "]")} contain errors")
        case Left(EnvelopeItemCountExceededError(allowed, actual)) =>
          ExceptionHandler(BAD_REQUEST, s"Envelope item count exceeds maximum of $allowed, actual: $actual")
        case Left(EnvelopeMaxSizeExceededError(allowedLimit)) =>
          ExceptionHandler(BAD_REQUEST, s"Envelope size exceeds maximum of ${ formatAsKiloOrMegabytes(allowedLimit) }")
        case Left(EnvelopeNotFoundError) =>
          ExceptionHandler(BAD_REQUEST, s"Envelope with id: $envelopeId not found")
        case Left(otherError) =>
          logger.warn(otherError.toString)
          ExceptionHandler(BAD_REQUEST, otherError.toString)
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def routingStatus(id: String) = Action {
    ExceptionHandler(NOT_IMPLEMENTED, "Not implemented as part of MVP")
  }
}
