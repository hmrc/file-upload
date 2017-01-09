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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.Xor
import play.api.http.HttpEntity
import play.api.libs.streams.Streams
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, WithValidEnvelope}
import uk.gov.hmrc.fileupload.read.file.Service._
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeNotFoundError, FileNotFoundError, StoreFile}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class FileController(withBasicAuth: BasicAuth,
                     uploadBodyParser: (EnvelopeId, FileId, FileRefId) => BodyParser[Future[JSONReadFile]],
                     retrieveFile: (Envelope, FileId) => Future[GetFileResult],
                     withValidEnvelope: WithValidEnvelope,
                     handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]])
                    (implicit executionContext: ExecutionContext) extends Controller {


  def upsertFile(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async(uploadBodyParser(id, fileId, fileRefId)) { request =>
    request.body.flatMap { jsonReadFile =>
      handleCommand(StoreFile(id, fileId, fileRefId, jsonReadFile.length)).map {
        case Xor.Right(_) => Ok
        case Xor.Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
        case Xor.Left(FileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found")
        case Xor.Left(_) => ExceptionHandler(INTERNAL_SERVER_ERROR, "File not added to envelope")
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def downloadFile(id: EnvelopeId, fileId: FileId) = Action.async { implicit request =>
    withBasicAuth {
      withValidEnvelope(id) { envelope =>
        retrieveFile(envelope, fileId).map {
          case Xor.Right(FileFound(filename, length, data)) =>
            val byteArray = Source.fromPublisher(Streams.enumeratorToPublisher(data.map(ByteString.fromArray)))
            Ok.sendEntity(HttpEntity.Streamed(byteArray, Some(length), Some("application/octet-stream")))
              .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="${filename.getOrElse("data")}"""",
                CONTENT_LENGTH -> s"$length",
                CONTENT_TYPE -> "application/octet-stream")
          case Xor.Left(GetFileNotFoundError) =>
            ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
        }
      }
    }
  }

}
