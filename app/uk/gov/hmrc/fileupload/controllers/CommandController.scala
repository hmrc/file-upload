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

import cats.data.Xor
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.write.envelope.Formatters._
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps


class CommandController(handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]])
                     (implicit executionContext: ExecutionContext) extends BaseController {

  def unsealEnvelope = Action.async(parse.json) { implicit req =>
    withCommand[UnsealEnvelope] { unsealEnvelope =>
      handleCommand(unsealEnvelope).map {
        case Xor.Right(_) => Ok
        case Xor.Left(EnvelopeNotFoundError) => envelopeNotFoundError(unsealEnvelope.id)
        case Xor.Left(EnvelopeAlreadyRoutedError | EnvelopeSealedError) => alreadyRoutedError(unsealEnvelope.id)
        case Xor.Left(a) => ExceptionHandler(BAD_REQUEST, a.toString)
      }
    }
  }

  def storeFile = Action.async(parse.json) { implicit req =>
    withCommand[StoreFile] { storeFile =>
      handleCommand(storeFile).map {
        case Xor.Right(_) => Ok
        case Xor.Left(EnvelopeNotFoundError) => envelopeNotFoundError(storeFile.id)
        case Xor.Left(EnvelopeAlreadyRoutedError | EnvelopeSealedError) => alreadyRoutedError(storeFile.id)
        case Xor.Left(FileAlreadyProcessed) => ExceptionHandler(BAD_REQUEST, s"File already processed, command was: $storeFile")
        case Xor.Left(error) => ExceptionHandler(BAD_REQUEST, error.toString)
      }
    }
  }

  def quarantineFile = Action.async(parse.json) { implicit req =>
    withCommand[QuarantineFile] { quarantineFile =>
      handleCommand(quarantineFile).map {
        case Xor.Right(_) => Ok
        case Xor.Left(EnvelopeAlreadyRoutedError | EnvelopeSealedError) => alreadyRoutedError(quarantineFile.id)
        case Xor.Left(a) => ExceptionHandler(BAD_REQUEST, a.toString)
      }
    }
  }

  def alreadyRoutedError(id: EnvelopeId) = ExceptionHandler(LOCKED, s"Routing request already received for envelope: $id")

  def envelopeNotFoundError(id: EnvelopeId) = ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")

  def withCommand[T <: EnvelopeCommand](f: EnvelopeCommand => Future[Result])
                                       (implicit r: Reads[T], m: Manifest[T], req: Request[JsValue]) = {
    Json.fromJson[T](req.body).asOpt.map { command =>
      f(command)
    }.getOrElse {
      Future.successful(ExceptionHandler(BAD_REQUEST, s"Unable to parse request as ${m.runtimeClass.getSimpleName}"))
    }
  }

}
