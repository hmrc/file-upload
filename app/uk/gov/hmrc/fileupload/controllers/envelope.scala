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
import uk.gov.hmrc.fileupload.read.envelope._
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes
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
    EnvelopeReport(id = Some(envelope._id),
                   callbackUrl = envelope.callbackUrl,
                   expiryDate = envelope.expiryDate,
                   status = Some(envelope.status.name),
                   metadata = envelope.metadata,
                   constraints = envelope.constraints,
                   destination = envelope.destination,
                   application = envelope.application,
                   files = fileReports)
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

object CreateEnvelopeRequest {

  import play.api.libs.json._

  implicit val dateReads: Reads[DateTime] = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsFormats: OFormat[EnvelopeConstraintsUserSetting] = Json.format[EnvelopeConstraintsUserSetting]
  implicit val formats: OFormat[CreateEnvelopeRequest] = Json.format[CreateEnvelopeRequest]

  def formatUserEnvelopeConstraints(constraintsO: EnvelopeConstraintsUserSetting,
                                    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration): Option[EnvelopeConstraints] = {
    import EnvelopeConstraints._

    val maxItems = constraintsO.maxItems.getOrElse(envelopeConstraintsConfigure.defaultMaxItems)
    val maxSize = constraintsO.maxSize.getOrElse(envelopeConstraintsConfigure.defaultMaxSize)
    val maxSizePerItem = constraintsO.maxSizePerItem.getOrElse(envelopeConstraintsConfigure.defaultMaxSizePerItem)

    val maxSizeInBytes: Long = translateToByteSize(maxSize)
    val maxSizePerItemInBytes: Long = translateToByteSize(maxSizePerItem)

    val acceptedMaxSizeInBytes: Long = translateToByteSize(envelopeConstraintsConfigure.acceptedMaxSize)
    val acceptedMaxSizePerItemInBytes: Long = translateToByteSize(envelopeConstraintsConfigure.acceptedMaxSizePerItem)

    require(isAValidSize(maxSize),
      s"Input constraints.maxSize is not a valid input, e.g 250MB")
    require(isAValidSize(maxSizePerItem),
      s"Input constraints.maxSizePerItem is not a valid input, e.g 100MB")
    require(!(maxSizeInBytes > acceptedMaxSizeInBytes),
      s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSize}")
    require(!(maxSizePerItemInBytes > acceptedMaxSizePerItemInBytes),
      s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSizePerItem}")
    require(maxSizeInBytes>=maxSizePerItemInBytes,
      s"constraints.maxSizePerItem can not greater than constraints.maxSize")

    Some(EnvelopeConstraints(maxItems = maxItems,
                             maxSize = maxSize.toUpperCase(),
                             maxSizePerItem = maxSizePerItem.toUpperCase(),
                             contentTypes = checkContentTypes(constraintsO.contentTypes.getOrElse(envelopeConstraintsConfigure.defaultContentTypes),
                                                              envelopeConstraintsConfigure.defaultContentTypes)
        ) )
  }

  def checkContentTypes(contentTypes: List[ContentTypes], defaultContentTypes: List[ContentTypes]): List[ContentTypes] = {
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
    GetFileMetadataReport(id = file.fileId,
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
