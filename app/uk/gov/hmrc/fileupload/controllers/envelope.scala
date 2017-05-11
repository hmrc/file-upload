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

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.fileupload.read.envelope.Envelope.{ContentTypes, defaultContentTypes, defaultMaxItems,
                                                      defaultMaxSize, defaultMaxSizePerItem}
import uk.gov.hmrc.fileupload.read.envelope._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class EnvelopeReport(id: Option[EnvelopeId] = None,
                          callbackUrl: Option[String] = None,
                          expiryDate: Option[DateTime] = None,
                          metadata: Option[JsObject] = None,
                          constraints: Option[EnvelopeConstraints] = None,
                          status: Option[String] = None,
                          destination: Option[String] = None,
                          application: Option[String] = None,
                          files: Option[Seq[GetFileMetadataReport]] = None)

object EnvelopeReport {
  implicit val dateWrites: Writes[DateTime] = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads: Reads[FileStatus] = FileStatusReads
  implicit val fileStatusWrites: Writes[FileStatus] = FileStatusWrites
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val envelopeConstraintsReads: Format[EnvelopeConstraints] = Json.format[EnvelopeConstraints]
  implicit val createEnvelopeReads: Format[EnvelopeReport] = Json.format[EnvelopeReport]

  def fromEnvelope(envelope: Envelope): EnvelopeReport = {
    val fileReports = envelope.files.map( _.map(file => GetFileMetadataReport.fromFile(envelope._id, file)) )
    EnvelopeReport(
      id = Some(envelope._id),
      callbackUrl = envelope.callbackUrl,
      expiryDate = envelope.expiryDate,
      status = Some(envelope.status.name),
      metadata = envelope.metadata,
      constraints = envelope.constraints,
      destination = envelope.destination,
      application = envelope.application,
      files = fileReports
    )
  }
}

case class CreateEnvelopeRequest(callbackUrl: Option[String] = None,
                                 expiryDate: Option[DateTime] = None,
                                 metadata: Option[JsObject] = None,
                                 constraints: Option[EnvelopeConstraintsUserSetting] = None)

case class EnvelopeConstraintsUserSetting(maxItems: Option[Int] = None,
                                          maxSize: Option[String] = None,
                                          maxSizePerItem: Option[String] = None,
                                          contentTypes: Option[List[ContentTypes]] = None)

case class EnvelopeConstraints(maxItems: Int,
                               maxSize: String,
                               maxSizePerItem: String,
                               contentTypes: List[ContentTypes]) {
  import EnvelopeConstraints._
  require(isAValidSize(maxSize), s"constraints.maxSize exceeds maximum allowed value of ${Envelope.acceptedConstraints.maxSize}")
  require(isAValidSize(maxSizePerItem), s"constraints.maxSizePerItem exceeds maximum allowed value of ${Envelope.acceptedConstraints.maxSizePerItem}")

  def maxSizeInBytes: Long = translateToByteSize(maxSize)
  def maxSizePerItemInBytes: Long = translateToByteSize(maxSizePerItem)
}

object EnvelopeConstraints {

  def isAValidSize(size: String): Boolean = {
    if (size.isEmpty) false
    else {
      val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r
      size.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => true
            case "MB" => true
            case _ => false
          }
        case _ => false
      }
    }
  }

  def translateToByteSize(size: String): Long = {
    if (size.isEmpty) throw new IllegalArgumentException(s"Invalid constraint input")
    else {
      val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r
      size.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => num.toInt * 1024
            case "MB" => num.toInt * 1024 * 1024
            case _ => throw new IllegalArgumentException(s"Invalid constraint input")
          }
        case _ => throw new IllegalArgumentException(s"Invalid constraint input")
      }
    }
  }
}

object CreateEnvelopeRequest {

  import play.api.libs.json._

  implicit val dateReads: Reads[DateTime] = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsFormats: OFormat[EnvelopeConstraintsUserSetting] = Json.format[EnvelopeConstraintsUserSetting]
  implicit val formats: OFormat[CreateEnvelopeRequest] = Json.format[CreateEnvelopeRequest]

  def formatUserEnvelopeConstraints(constraintsO: EnvelopeConstraintsUserSetting): Option[EnvelopeConstraints] = {
    Some(EnvelopeConstraints(maxItems = constraintsO.maxItems.getOrElse(defaultMaxItems),
                        maxSize = constraintsO.maxSize.getOrElse(defaultMaxSize).toUpperCase(),
                        maxSizePerItem = constraintsO.maxSizePerItem.getOrElse(defaultMaxSizePerItem).toUpperCase(),
                        contentTypes = checkContentTypes(constraintsO.contentTypes.getOrElse(defaultContentTypes))
        ) )
  }

  def checkContentTypes(contentTypes: List[ContentTypes]): List[ContentTypes] ={
    if (contentTypes.isEmpty) defaultContentTypes
    else contentTypes
  }

}

case class GetFileMetadataReport(id: FileId,
                                 status: Option[String] = None,
                                 name: Option[String] = None,
                                 contentType: Option[String] = None,
                                 length: Option[Long] = None,
                                 created: Option[DateTime] = None,
                                 revision: Option[Int] = None,
                                 metadata: Option[JsObject] = None,
                                 href: Option[String] = None)

object GetFileMetadataReport {
  implicit val dateReads: Reads[DateTime] = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites: Writes[DateTime] = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val getFileMetaDataReportFormat: Format[GetFileMetadataReport] = Json.format[GetFileMetadataReport]

  def href(envelopeId: EnvelopeId, fileId: FileId): String = {
    uk.gov.hmrc.fileupload.controllers.routes.FileController.downloadFile(envelopeId, fileId).url
  }

  def fromFile(envelopeId: EnvelopeId, file: File): GetFileMetadataReport =
    GetFileMetadataReport(
      id = file.fileId,
      status = Some(file.status.name),
      name = file.name,
      contentType = file.contentType,
      length = file.length,
      created = file.uploadDate,
      metadata = file.metadata,
      href = Some(href(envelopeId, file.fileId))
    )
}

case class GetEnvelopesByStatus(status: List[EnvelopeStatus], inclusive: Boolean)

object GetEnvelopesByStatus {

  implicit def getEnvelopesByStatusQueryStringBindable(implicit booleanBinder: QueryStringBindable[Boolean],
                                                       listBinder: QueryStringBindable[List[String]]) =
    new QueryStringBindable[GetEnvelopesByStatus] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, GetEnvelopesByStatus]] = {
        for {
          status <- listBinder.bind("status", params)
          inclusive <- booleanBinder.bind("inclusive", params)
        } yield {
          (status, inclusive) match {
            case (Right(s), Right(i)) => Right(GetEnvelopesByStatus(s.map(EnvelopeStatusTransformer.fromName), i))
            case _ => Left("Unable to bind a GetEnvelopesByStatus")
          }
        }
      }

      override def unbind(key: String, getEnvelopesByStatus: GetEnvelopesByStatus): String = {
        val statuses = getEnvelopesByStatus.status.map(n => s"status=$n")
        statuses.mkString("&") + "&" + booleanBinder.unbind("inclusive", getEnvelopesByStatus.inclusive)
      }
  }
}
