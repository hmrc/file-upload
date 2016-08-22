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

import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.fileupload.events.EventFormatters._
import uk.gov.hmrc.fileupload.events._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class EventController(publish: (AnyRef) => Unit )(implicit executionContext: ExecutionContext) extends BaseController {

  def collect(eventType: String) = Action.async(EventParser) { implicit request =>
    publish(request.body)
    Future.successful(Ok)
  }

}

object EventParser extends BodyParser[Event] {

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, Event]] = {
    val pattern =  "events/(.+)$".r.unanchored

    Iteratee.consume[Array[Byte]]().map { data =>
      val parsedData = Json.parse(data)

      val triedEvent: Try[Event] = request.uri match {
        case pattern(eventType) =>
          eventType.toLowerCase match  {
            case "novirusdetected" => Try(Json.fromJson[NoVirusDetected](parsedData).get)
            case "quarantined" => Try(Json.fromJson[Quarantined](parsedData).get)
            case "totransientmoved" => Try(Json.fromJson[ToTransientMoved](parsedData).get)
            case "movingtotransientfailed" => Try(Json.fromJson[MovingToTransientFailed](parsedData).get)
            case "virusdetected" => Try(Json.fromJson[VirusDetected](parsedData).get)
            case _ => Failure(new InvalidEventException(s"$eventType is not a valid event"))
          }

      }

      triedEvent match {
        case Success(event) => Right(event)
        case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
      }
    }(ExecutionContext.global)
  }
}

class InvalidEventException(reason: String) extends IllegalArgumentException(reason)
