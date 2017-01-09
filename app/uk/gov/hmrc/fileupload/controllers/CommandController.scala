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

import akka.util.ByteString
import cats.data.Xor
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.utils.StreamUtils
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{EventSerializer => _, _}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class CommandController(handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]])
                     (implicit executionContext: ExecutionContext) extends BaseController {

  def handle(commandType: String) = Action.async(CommandParser) { implicit request =>
    handleCommand(request.body).map {
          case Xor.Right(_) => Ok
          case Xor.Left(EnvelopeAlreadyRoutedError | EnvelopeSealedError) =>
            ExceptionHandler(LOCKED, s"Routing request already received for envelope: ${request.body.id}")
          case Xor.Left(a) => ExceptionHandler(BAD_REQUEST, a.toString)
        }
  }
}

object CommandParser extends BodyParser[EnvelopeCommand] {

  def apply(request: RequestHeader): Accumulator[ByteString, Either[Result, EnvelopeCommand]] = {
    import Formatters._
    val pattern =  "commands/(.+)$".r.unanchored

    import play.api.libs.concurrent.Execution.Implicits._
    StreamUtils.iterateeToAccumulator(Iteratee.consume[ByteStream]()).map { data =>
      val parsedData = Json.parse(data)

      val triedEvent: Try[EnvelopeCommand] = request.uri match {
        case pattern(commandType) =>
          commandType.toLowerCase match  {
            case "unsealenvelope" => Try(Json.fromJson[UnsealEnvelope](parsedData).get)
            case _ => Failure(new InvalidCommandException(s"$commandType is not a valid event"))
          }
      }

      triedEvent match {
        case Success(event) => Right(event)
        case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
      }
    }(ExecutionContext.global)
  }
}

class InvalidCommandException(reason: String) extends IllegalArgumentException(reason)
