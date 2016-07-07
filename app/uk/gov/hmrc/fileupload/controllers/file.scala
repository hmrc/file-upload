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
import play.api.mvc.{BodyParser, _}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file.FileMetadata

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object FileMetadataParser extends BodyParser[FileMetadata] {

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, FileMetadata]] = {
    import FileMetadata._

    Iteratee.consume[Array[Byte]]().map { data =>
      Try(Json.fromJson[FileMetadata](Json.parse(data)).get) match {
        case Success(fileMetadata) => Right(fileMetadata)
        case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
      }
    }(ExecutionContext.global)
  }
}

object UploadParser {

  def parse(uploadFile: String => Iteratee[ByteStream, Future[JSONReadFile]])
           (envelopeId: String, fileId: String)
           (implicit ex: ExecutionContext): BodyParser[Future[JSONReadFile]] = BodyParser { _ =>

    uploadFile(fileId) map (Right(_)) recover { case NonFatal(e) => Left(ExceptionHandler(e)) }
  }
}

//object FileUploadValidationFilter extends Filter {
//
//  implicit val defaultTimeout = Timeout(2 seconds)
//  implicit val ec = ExecutionContext.global
//
//  val envelopeService = Actors.envelopeService
//
//  override def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
//
//    val putFilePattern = "/file-upload/envelope/(.*)/file/(.*)/content".r
//
//    (requestHeader.method, requestHeader.path) match {
//      case ("PUT", putFilePattern(envelopeId, fileId)) =>
//        (envelopeService ? GetEnvelope(envelopeId))
//          .breakOnFailure
//          .flatMap {
//            case e: Envelope if e.contains(fileId)  => Future.successful(Results.BadRequest)
//            case _ => nextFilter(requestHeader)
//          }.recover{ case e => Results.NotFound }
//      case _ => nextFilter(requestHeader)
//    }
//  }
//}

