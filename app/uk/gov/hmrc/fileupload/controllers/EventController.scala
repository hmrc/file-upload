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
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.EventFormatters._
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore.GetResult
import uk.gov.hmrc.fileupload.write.infrastructure.{EventSerializer => _, _}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import uk.gov.hmrc.fileupload.write.infrastructure.{Event => DomainEvent}

class EventController(handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
                      unitOfWorks: StreamId => Future[GetResult], publishAllEvents: Seq[DomainEvent] => Unit)
                     (implicit executionContext: ExecutionContext) extends BaseController {

  implicit val eventWrites = EventSerializer.eventWrite

  def collect(eventType: String) = Action.async(EventParser) { implicit request =>
    request.body match {
      case e: FileInQuarantineStored =>
        val command = QuarantineFile(e.envelopeId, e.fileId, e.fileRefId, e.created, e.name, e.contentType, e.metadata)
        handleCommand(command).map {
          case Xor.Right(_) => Ok
          case Xor.Left(EnvelopeAlreadyRoutedError | EnvelopeSealedError) =>
            ExceptionHandler(LOCKED, s"Routing request already received for envelope: ${e.envelopeId}")
          case Xor.Left(a) => ExceptionHandler(BAD_REQUEST, a.toString)
        }
      case e: FileScanned =>
        val command = if (e.hasVirus) {
          MarkFileAsInfected(e.envelopeId, e.fileId, e.fileRefId)
        } else {
          MarkFileAsClean(e.envelopeId, e.fileId, e.fileRefId)
        }
        handleCommand(command).map {
          case Xor.Right(_) => Ok
          case Xor.Left(a) => ExceptionHandler(BAD_REQUEST, a.toString)
        }
    }
  }

  def get(streamId: StreamId) = Action.async { implicit request =>
    unitOfWorks(streamId) map {
      case Xor.Right(r) =>
        Ok(Json.toJson(r.flatMap(_.events)))
      case Xor.Left(e) =>
        ExceptionHandler(INTERNAL_SERVER_ERROR, e.message)
    }
  }

  def replay(streamId: StreamId) = Action.async { implicit request =>
    unitOfWorks(streamId).map {
      case Xor.Right(sequence) =>
        publishAllEvents(sequence.flatMap( _.events ))
        Ok
      case Xor.Left(error) => InternalServerError(s"Unexpected result: ${error.message}")
    }
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
            case "filescanned" => Try(Json.fromJson[FileScanned](parsedData).get)
            case "fileinquarantinestored" => Try(Json.fromJson[FileInQuarantineStored](parsedData).get)
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
