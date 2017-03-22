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

package uk.gov.hmrc.fileupload.controllers.routing

import java.util.UUID

import cats.data.Xor
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, JsValue, Reads}
import play.api.mvc.{Action, Controller, Request, Result}
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class RoutingController(handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                        newId: () => String = () => UUID.randomUUID().toString)
                       (implicit executionContext: ExecutionContext) extends Controller {

  def createRoutingRequest() = Action.async(parse.json) { implicit request =>
    withJsonBody[RouteEnvelopeRequest] { requestParams =>
      import requestParams._
      val requestId = newId()
      handleCommand(SealEnvelope(envelopeId, requestId, destination, application)).map {
        case Xor.Right(_) => Created.withHeaders(LOCATION -> uk.gov.hmrc.fileupload.controllers.routing.routes.RoutingController.routingStatus(requestId).url)
        case Xor.Left(EnvelopeSealedError) | Xor.Left(EnvelopeAlreadyRoutedError) =>
          ExceptionHandler(BAD_REQUEST, s"Routing request already received for envelope: $envelopeId")
        case Xor.Left(FilesWithError(ids)) =>
          ExceptionHandler(BAD_REQUEST, s"Files: ${ids.mkString("[", ", ", "]")} contain errors")
        case Xor.Left(EnvelopeItemCountExceededError(allowed, actual)) =>
          ExceptionHandler(BAD_REQUEST, s"Too many files in the envelope allowance: $allowed actual: $actual")
        case Xor.Left(EnvelopeMaxSizeExceededError(allowedLimit)) =>
          ExceptionHandler(BAD_REQUEST, s"The total size of the files in envelope exceeds the limit $allowedLimit")
        case Xor.Left(EnvelopeNotFoundError) =>
          ExceptionHandler(BAD_REQUEST, s"Envelope with id: $envelopeId not found")
        case Xor.Left(otherError) =>
          Logger.warn(otherError.toString)
          ExceptionHandler(BAD_REQUEST, otherError.toString)
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def routingStatus(id: String) = Action {
    ExceptionHandler(NOT_IMPLEMENTED, "Not implemented as part of MVP")
  }

  private def withJsonBody[T](f: (T) => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]) =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future.successful(BadRequest(s"Invalid ${m.runtimeClass.getSimpleName} payload: $errs"))
      case Failure(e) => Future.successful(BadRequest(s"could not parse body due to ${e.getMessage}"))
    }

}
