/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.syntax.all._
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.fileupload.read.envelope._
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileName}

case class EnvelopeReport(
  id         : Option[EnvelopeId]                 = None,
  callbackUrl: Option[String]                     = None,
  expiryDate : Option[DateTime]                   = None,
  metadata   : Option[JsObject]                   = None,
  constraints: Option[EnvelopeFilesConstraints]   = None,
  status     : Option[String]                     = None,
  destination: Option[String]                     = None,
  application: Option[String]                     = None,
  files      : Option[Seq[GetFileMetadataReport]] = None
)

object EnvelopeReport {
  implicit val dateReads               : Reads[DateTime]                  = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites              : Writes[DateTime]                 = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads         : Reads[FileStatus]                = FileStatusReads
  implicit val fileStatusWrites        : Writes[FileStatus]               = FileStatusWrites
  implicit val fileFormat              : Format[File]                     = {
    implicit val fnf: Format[FileName] = FileName.apiFormat
    Json.format[File]
  }
  implicit val sizeReads               : Reads[Size]                      = SizeReads
  implicit val sizeWrites              : Writes[Size]                     = SizeWrites
  implicit val envelopeConstraintsReads: Format[EnvelopeFilesConstraints] = Json.format[EnvelopeFilesConstraints]
  implicit val createEnvelopeReads     : Format[EnvelopeReport]           = Json.format[EnvelopeReport]

  def fromEnvelope(envelope: Envelope): EnvelopeReport =
    EnvelopeReport(
      id          = Some(envelope._id),
      callbackUrl = envelope.callbackUrl,
      expiryDate  = envelope.expiryDate,
      status      = Some(envelope.status.name),
      metadata    = envelope.metadata,
      constraints = envelope.constraints,
      destination = envelope.destination,
      application = envelope.application,
      files       = envelope.files.map(_.map(file => GetFileMetadataReport.fromFile(envelope._id, file)))
    )
  }

case class CreateEnvelopeRequest(
  callbackUrl: Option[String]                         = None,
  expiryDate : Option[DateTime]                       = None,
  metadata   : Option[JsObject]                       = None,
  constraints: Option[EnvelopeConstraintsUserSetting] = None
)

case class EnvelopeConstraintsUserSetting(
  maxItems            : Option[Int]                = None,
  maxSize             : Option[String]             = None,
  maxSizePerItem      : Option[String]             = None,
  contentTypes        : Option[List[ContentTypes]] = None,
  allowZeroLengthFiles: Option[Boolean]            = None
)

object CreateEnvelopeRequest {
  import play.api.libs.json._

  implicit val dateReads         : Reads[DateTime]                         = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites        : Writes[DateTime]                        = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsFormats: OFormat[EnvelopeConstraintsUserSetting] = Json.format[EnvelopeConstraintsUserSetting]
  implicit val formats           : OFormat[CreateEnvelopeRequest]          = Json.format[CreateEnvelopeRequest]
}

case class GetFileMetadataReport(
  id         : FileId,
  status     : Option[String]   = None,
  name       : Option[FileName] = None,
  contentType: Option[String]   = None,
  length     : Option[Long]     = None,
  created    : Option[DateTime] = None,
  revision   : Option[Int]      = None,
  metadata   : Option[JsObject] = None,
  href       : Option[String]   = None
)

object GetFileMetadataReport {

  implicit val getFileMetaDataReportFormat: Format[GetFileMetadataReport] = {
    implicit val dr  : Reads[DateTime]  = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
    implicit val dw  : Writes[DateTime] = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
    implicit val fnf : Format[FileName] = FileName.apiFormat
    Json.format[GetFileMetadataReport]
  }

  def href(envelopeId: EnvelopeId, fileId: FileId): String =
    uk.gov.hmrc.fileupload.controllers.routes.FileController.downloadFile(envelopeId, fileId).url

  def fromFile(envelopeId: EnvelopeId, file: File): GetFileMetadataReport =
    GetFileMetadataReport(
      id          = file.fileId,
      status      = Some(file.status.name),
      name        = file.name,
      contentType = file.contentType,
      length      = file.length,
      created     = file.uploadDate,
      metadata    = file.metadata,
      href        = Some(href(envelopeId, file.fileId))
    )
}

case class GetEnvelopesByStatus(
  status   : List[EnvelopeStatus],
  inclusive: Boolean
)

object GetEnvelopesByStatus {

  implicit def getEnvelopesByStatusQueryStringBindable(implicit
    booleanBinder: QueryStringBindable[Boolean],
    listBinder   : QueryStringBindable[List[String]]
  ): QueryStringBindable[GetEnvelopesByStatus] =
    new QueryStringBindable[GetEnvelopesByStatus] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, GetEnvelopesByStatus]] =
        for {
          statusStr <- listBinder.bind("status", params)
          status    =  statusStr.flatMap(l => Either.fromOption(l.traverse(EnvelopeStatusTransformer.fromName), "Invalid status"))
          inclusive <- booleanBinder.bind("inclusive", params)
        } yield {
          (status, inclusive) match {
            case (Right(s), Right(i)) => Right(GetEnvelopesByStatus(s, i))
            case _ => Left("Unable to bind a GetEnvelopesByStatus")
          }
        }

      override def unbind(key: String, getEnvelopesByStatus: GetEnvelopesByStatus): String = {
        val statuses = getEnvelopesByStatus.status.map(n => s"status=$n")
        statuses.mkString("&") + "&" + booleanBinder.unbind("inclusive", getEnvelopesByStatus.inclusive)
      }
  }
}
