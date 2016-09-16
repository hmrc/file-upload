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

package uk.gov.hmrc.fileupload.controllers.routing

import java.util.UUID

import cats.data.Xor
import play.api.mvc.Action
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, SealEnvelope}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class RoutingController(handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                        newId: () => String = () => UUID.randomUUID().toString)
                       (implicit executionContext: ExecutionContext) extends BaseController {

  def route() = Action.async(parse.json) { implicit request =>
    withJsonBody[RouteEnvelopeRequest] { requestParams =>
      import requestParams._
      handleCommand(SealEnvelope(envelope, destination, application)).map {
        case Xor.Right(_) => Created.withHeaders(LOCATION -> routes.RoutingController.routingStatus("foo").url)
        case Xor.Left(_) => InternalServerError
      }
    }
  }

  def routingStatus(id: String) = Action { NotImplemented("Not implemented as part of MVP") }

}

