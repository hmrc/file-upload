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

package uk.gov.hmrc.fileupload.read.envelope

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, _}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

case class Envelope(_id: EnvelopeId = EnvelopeId(),
                    status: EnvelopeStatus = EnvelopeStatusOpen,
                    constraints: Option[Constraints] = None,
                    callbackUrl: Option[String] = None, expiryDate: Option[DateTime] = None,
                    metadata: Option[Map[String, JsValue]] = None, files: Option[Seq[File]] = None)

case class Constraints(contentTypes: Option[Seq[String]] = None,
                       maxItems: Option[Int] = None,
                       maxSize: Option[String] = None,
                       maxSizePerItem: Option[String] = None)

case class File(fileId: FileId,
                fileReferenceId: FileRefId,
                status: FileStatus,
                name: Option[String] = None,
                contentType: Option[String] = None,
                length: Option[Long] = None,
                uploadDate: Option[DateTime] = None,
                revision: Option[Int] = None,
                metadata: Option[JsObject] = None,
                rel: Option[String] = Some("file"))

object Envelope {

  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads: Reads[FileStatus] = FileStatusReads
  implicit val fileStatusWrites: Writes[FileStatus] = FileStatusWrites
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val envelopeStatusReads: Reads[EnvelopeStatus] = EnvelopeStatusReads
  implicit val envelopeStatusWrites: Writes[EnvelopeStatus] = EnvelopeStatusWrites
  implicit val constraintsReads: Format[Constraints] = Json.format[Constraints]
  implicit val envelopeFormat: Format[Envelope] = Json.format[Envelope]
  implicit val envelopeOFormat: OFormat[Envelope] = new OFormat[Envelope] {
    def reads(json: JsValue): JsResult[Envelope] = envelopeFormat.reads(json)
    def writes(o: Envelope): JsObject = envelopeFormat.writes(o).as[JsObject]
  }

  def fromJson(json: JsValue, _id: EnvelopeId, maxTTL: Int): Envelope = {
    val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id)
    val envelope = Json.fromJson[Envelope](rawData).get
    val maxExpiryDate: DateTime = DateTime.now().plusDays(maxTTL)

    val expiryDate = envelope.expiryDate.map(d => if (d.isBefore(maxExpiryDate)) d else maxExpiryDate)
    envelope.copy(expiryDate = expiryDate)
  }
}

case class ValidationException(reason: String) extends IllegalArgumentException(reason)

sealed trait EnvelopeStatus {
  def name: String
}
case object EnvelopeStatusOpen extends EnvelopeStatus {
  override val name: String = "OPEN"
}
case object EnvelopeStatusClosed extends EnvelopeStatus {
  override val name: String = "CLOSED"
}
case object EnvelopeStatusAvailable extends EnvelopeStatus {
  override val name: String = "AVAILABLE"
}
case object EnvelopeStatusDeleted extends EnvelopeStatus {
  override val name: String = "DELETED"
}

object EnvelopeStatusWrites extends Writes[EnvelopeStatus] {
  def writes(c: EnvelopeStatus) = c match {
    case EnvelopeStatusOpen => Json.toJson(EnvelopeStatusOpen.name)
    case EnvelopeStatusClosed => Json.toJson(EnvelopeStatusClosed.name)
    case EnvelopeStatusAvailable => Json.toJson(EnvelopeStatusAvailable.name)
    case EnvelopeStatusDeleted => Json.toJson(EnvelopeStatusDeleted.name)
  }
}

object EnvelopeStatusReads extends Reads[EnvelopeStatus] {
  def reads(value: JsValue) = value.as[String] match {
    case EnvelopeStatusOpen.name => JsSuccess(EnvelopeStatusOpen)
    case EnvelopeStatusClosed.name => JsSuccess(EnvelopeStatusClosed)
    case EnvelopeStatusAvailable.name => JsSuccess(EnvelopeStatusAvailable)
    case EnvelopeStatusDeleted.name => JsSuccess(EnvelopeStatusDeleted)
  }
}

sealed trait FileStatus {
  def name: String
}
case object FileStatusQuarantined extends FileStatus {
  override val name: String = "QUARANTINED"
}
case object FileStatusCleaned extends FileStatus {
  override val name: String = "CLEANED"
}
case object FileStatusAvailable extends FileStatus {
  override val name: String = "AVAILABLE"
}

object FileStatusWrites extends Writes[FileStatus] {
  def writes(c: FileStatus) = c match {
    case FileStatusQuarantined => Json.toJson(FileStatusQuarantined.name)
    case FileStatusCleaned => Json.toJson(FileStatusCleaned.name)
    case FileStatusAvailable => Json.toJson(FileStatusAvailable.name)
  }
}

object FileStatusReads extends Reads[FileStatus] {
  def reads(value: JsValue) = value.as[String] match {
    case FileStatusQuarantined.name => JsSuccess(FileStatusQuarantined)
    case FileStatusCleaned.name => JsSuccess(FileStatusCleaned)
    case FileStatusAvailable.name => JsSuccess(FileStatusAvailable)
  }
}
