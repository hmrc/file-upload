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

import org.joda.time.DateTime
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.mvc.{BodyParser, _}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file.{CompositeFileId, FileMetadata}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class UpdateFileMetadataReport(name: Option[String] = None,
                                    contentType: Option[String] = None,
                                    revision: Option[Int] = None,
                                    metadata: Option[JsObject] = None)

object UpdateFileMetadataReport {
  implicit val updateFileMetaDataReportFormat: Format[UpdateFileMetadataReport] = Json.format[UpdateFileMetadataReport]

  def toFileMetadata(envelopeId: String, fileId: String, report: UpdateFileMetadataReport): FileMetadata =
    FileMetadata(
      _id = CompositeFileId(envelopeId = envelopeId, fileId = fileId),
      name = report.name,
      contentType = report.contentType,
      revision = report.revision,
      metadata = report.metadata)
}

case class GetFileMetadataReport(id: String,
                                 name: Option[String] = None,
                                 contentType: Option[String] = None,
                                 length: Option[Long] = None,
                                 created: Option[DateTime] = None,
                                 revision: Option[Int] = None,
                                 metadata: Option[JsObject] = None)

object GetFileMetadataReport {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val getFileMetaDataReportFormat: Format[GetFileMetadataReport] = Json.format[GetFileMetadataReport]

  def fromFileMetadata(fileMetadata: FileMetadata): GetFileMetadataReport =
    GetFileMetadataReport(
      id = fileMetadata._id.fileId,
      name = fileMetadata.name,
      contentType = fileMetadata.contentType,
      length = fileMetadata.length,
      created = fileMetadata.uploadDate,
      revision = fileMetadata.revision,
      metadata = fileMetadata.metadata)
}

object FileMetadataParser extends BodyParser[UpdateFileMetadataReport] {

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, UpdateFileMetadataReport]] = {
    import UpdateFileMetadataReport._

    Iteratee.consume[Array[Byte]]().map { data =>
      Try(Json.fromJson[UpdateFileMetadataReport](Json.parse(data)).get) match {
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

