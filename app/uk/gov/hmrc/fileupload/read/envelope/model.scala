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

package uk.gov.hmrc.fileupload.read.envelope

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.EnvelopeConstraints
import uk.gov.hmrc.fileupload.write.infrastructure.Version
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

case class Envelope(_id: EnvelopeId = EnvelopeId(),
                    version: Version = Version(0),
                    status: EnvelopeStatus = EnvelopeStatusOpen,
                    constraints: Option[EnvelopeConstraints] = None,
                    callbackUrl: Option[String] = None,
                    expiryDate: Option[DateTime] = None,
                    metadata: Option[JsObject] = None,
                    files: Option[Seq[File]] = None,
                    destination: Option[String] = None,
                    application: Option[String] = None) {

  def getFileById(fileId: FileId): Option[File] = {
    files.flatMap { _.find { file => file.fileId == fileId }}
  }
}

case class File(fileId: FileId,
                fileRefId: FileRefId,
                status: FileStatus,
                name: Option[String] = None,
                contentType: Option[String] = None,
                length: Option[Long] = None,
                uploadDate: Option[DateTime] = None,
                revision: Option[Int] = None,
                metadata: Option[JsObject] = None,
                rel: Option[String] = Some("file"))

object Envelope {

  type ContentTypes = String

  val acceptedMaxItems: Int = 100
  val acceptedMaxSize: String = "250MB" //250 * 1024 * 1024
  val acceptedMaxSizePerItem: String = "100MB" //100 * 1024 * 1024
  val acceptedContentTypes: List[ContentTypes] =
                                List("application/pdf",
                                     "image/jpeg",
                                     "text/xml",
                                     "application/xml",
                                     "application/vnd.ms-excel",
                                     "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

  val defaultMaxItems: Int = 100
  val defaultMaxSize: String = "25MB" //25 * 1024 * 1024
  val defaultMaxSizePerItem: String = "10MB" //10 * 1024 * 1024
  val defaultContentTypes: List[ContentTypes] = List("application/pdf","image/jpeg","application/xml","text/xml")

  val defaultConstraints =
    EnvelopeConstraints(maxItems = defaultMaxItems,
                        maxSize = defaultMaxSize,
                        maxSizePerItem = defaultMaxSizePerItem,
                        contentTypes = defaultContentTypes)

  val acceptedConstraints =
    EnvelopeConstraints(maxItems = acceptedMaxItems,
                        maxSize = acceptedMaxSize,
                        maxSizePerItem = acceptedMaxSizePerItem,
                        contentTypes = acceptedContentTypes)

  implicit val dateReads: Reads[DateTime] = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites: Writes[DateTime] = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads: Reads[FileStatus] = FileStatusReads
  implicit val fileStatusWrites: Writes[FileStatus] = FileStatusWrites
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val envelopeStatusReads: Reads[EnvelopeStatus] = EnvelopeStatusReads
  implicit val envelopeStatusWrites: Writes[EnvelopeStatus] = EnvelopeStatusWrites
  implicit val envelopeConstraintsFormats: OFormat[EnvelopeConstraints] = Json.format[EnvelopeConstraints]
  implicit val envelopeFormat: Format[Envelope] = Json.format[Envelope]
  implicit val envelopeOFormat: OFormat[Envelope] = new OFormat[Envelope] {
    def reads(json: JsValue): JsResult[Envelope] = envelopeFormat.reads(json)
    def writes(o: Envelope): JsObject = envelopeFormat.writes(o).as[JsObject]
  }

  def fromJson(json: JsValue, _id: EnvelopeId): Envelope = {
    val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id)
    Json.fromJson[Envelope](rawData).get
  }
}

case class ValidationException(reason: String) extends IllegalArgumentException(reason)

sealed trait EnvelopeStatus {
  def name: String
}
case object EnvelopeStatusOpen extends EnvelopeStatus {
  override val name: String = "OPEN"
}
case object EnvelopeStatusSealed extends EnvelopeStatus {
  override val name: String = "SEALED"
}
case object EnvelopeStatusClosed extends EnvelopeStatus {
  override val name: String = "CLOSED"
}
case object EnvelopeStatusDeleted extends EnvelopeStatus {
  override val name: String = "DELETED"
}

object EnvelopeStatusWrites extends Writes[EnvelopeStatus] {
  def writes(c: EnvelopeStatus): JsValue = Json.toJson(c.name)
}

object EnvelopeStatusReads extends Reads[EnvelopeStatus] {
  def reads(value: JsValue) = JsSuccess(EnvelopeStatusTransformer.fromName(value.as[String]))
}

object EnvelopeStatusTransformer {
  def fromName(name: String): EnvelopeStatus =
    name match {
      case EnvelopeStatusOpen.name => EnvelopeStatusOpen
      case EnvelopeStatusSealed.name => EnvelopeStatusSealed
      case EnvelopeStatusClosed.name => EnvelopeStatusClosed
      case EnvelopeStatusDeleted.name => EnvelopeStatusDeleted
    }
}

sealed trait FileStatus {
  def name: String
}
case object FileStatusQuarantined extends FileStatus {
  override val name = "QUARANTINED"
}
case object FileStatusCleaned extends FileStatus {
  override val name = "CLEANED"
}
case object FileStatusAvailable extends FileStatus {
  override val name = "AVAILABLE"
}

// todo this needs reason as well, Why is it an error?
case object FileStatusError extends FileStatus {
  override val name = "ERROR"
}

object FileStatusWrites extends Writes[FileStatus] {
  def writes(c: FileStatus): JsValue = Json.toJson(c.name)
}

object FileStatusReads extends Reads[FileStatus] {
  def reads(value: JsValue) = value.as[String] match {
    case FileStatusQuarantined.name => JsSuccess(FileStatusQuarantined)
    case FileStatusCleaned.name => JsSuccess(FileStatusCleaned)
    case FileStatusAvailable.name => JsSuccess(FileStatusAvailable)
    case FileStatusError.name => JsSuccess(FileStatusError)
  }
}
