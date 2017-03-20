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

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import cats.data.Xor
import play.api.http.HttpEntity
import play.api.libs.streams.Streams
import play.api.libs.ws.WSClient
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.BasicAuth
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, WithValidEnvelope}
import uk.gov.hmrc.fileupload.read.file.Service._
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeNotFoundError, FileNotFoundError, StoreFile}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

//case class FileFound2(name: Option[String], length: Option[Long])
//
//class RetrieveFile(wsClient: WSClient, baseUrl: String) {
//  def apply(envelope: Envelope, fileId: FileId): Future[GetFileResult] = {
//
//    val downloadUrl = s"$baseUrl/file-upload/download/envelopes/${envelope._id}/files/$fileId"
//    wsClient.url(downloadUrl).stream().map(_.body).map { stream =>
//      Xor.fromOption(envelope.getFileById(fileId).map { f =>
//        (f.name, f.length)
//      }, ifNone = GetFileNotFoundError).map {
//        case (name, length) =>
//          FileFound(name, length, Streams.publisherToEnumerator(stream.as))
//
//      }
//    }
//
//  }
//}

class FileController(withBasicAuth: BasicAuth,
                     retrieveFile: (Envelope, FileId) => Future[GetFileResult],
                     withValidEnvelope: WithValidEnvelope,
                     handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]])
                    (implicit executionContext: ExecutionContext) extends Controller {

  // TODO (konrad-s3-migration): replace this with downloading from S3 however we need to proxy via front-end :(
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
