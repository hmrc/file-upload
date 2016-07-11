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
import play.api.libs.json.{Format, JsObject, Json}
import play.api.mvc.{BodyParser, _}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file.{CompositeFileId, FileMetadata}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class FileMetadataReport(id: Option[String],
                              filename: Option[String] = None,
                              contentType: Option[String] = None,
                              revision: Option[Int] = None,
                              metadata: Option[JsObject] = None)

object FileMetadataReport {
  implicit val fileMetaDataReportFormat: Format[FileMetadataReport] = Json.format[FileMetadataReport]

  def toFileMetadata(envelopeId: String, fileId: String, report: FileMetadataReport): FileMetadata =
    FileMetadata(
      _id = CompositeFileId(envelopeId = envelopeId, fileId = fileId),
      filename = report.filename,
      contentType = report.contentType,
      revision = report.revision,
      metadata = report.metadata)

  def fromFileMetadata(fileMetadata: FileMetadata): FileMetadataReport =
    FileMetadataReport(
      id = Some(fileMetadata._id.fileId),
      filename = fileMetadata.filename,
      contentType = fileMetadata.contentType,
      revision = fileMetadata.revision,
      metadata = fileMetadata.metadata)
}

object FileMetadataParser extends BodyParser[FileMetadataReport] {

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, FileMetadataReport]] = {
    import FileMetadataReport._

    Iteratee.consume[Array[Byte]]().map { data =>
      Try(Json.fromJson[FileMetadataReport](Json.parse(data)).get) match {
        case Success(report) => Right(report)
        case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
      }
    }(ExecutionContext.global)
  }
}

object UploadParser {

  def parse(uploadFile: CompositeFileId => Iteratee[ByteStream, Future[JSONReadFile]])
           (compositeFileId: CompositeFileId)
           (implicit ex: ExecutionContext): BodyParser[Future[JSONReadFile]] = BodyParser { _ =>

    uploadFile(compositeFileId) map (Right(_)) recover { case NonFatal(e) => Left(ExceptionHandler(e)) }
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

