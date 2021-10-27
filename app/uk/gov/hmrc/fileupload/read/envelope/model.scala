/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.write.infrastructure.Version
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

case class Envelope(_id: EnvelopeId = EnvelopeId(),
                    version: Version = Version(0),
                    status: EnvelopeStatus = EnvelopeStatusOpen,
                    constraints: Option[EnvelopeFilesConstraints] = None,
                    callbackUrl: Option[String] = None,
                    expiryDate: Option[DateTime] = None,
                    metadata: Option[JsObject] = None,
                    files: Option[Seq[File]] = None,
                    destination: Option[String] = None,
                    application: Option[String] = None,
                    isPushed: Option[Boolean] = None) {

  def getFileById(fileId: FileId): Option[File] =
    files.flatMap(_.find(_.fileId == fileId))
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
  implicit val dateReads: Reads[DateTime] = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites: Writes[DateTime] = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads: Reads[FileStatus] = FileStatusReads
  implicit val fileStatusWrites: Writes[FileStatus] = FileStatusWrites
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val envelopeStatusReads: Reads[EnvelopeStatus] = EnvelopeStatusReads
  implicit val envelopeStatusWrites: Writes[EnvelopeStatus] = EnvelopeStatusWrites
  implicit val sizeReads: Reads[Size] = SizeReads
  implicit val sizeWrites: Writes[Size] = SizeWrites
  implicit val envelopeConstraintsFormats: OFormat[EnvelopeFilesConstraints] = Json.format[EnvelopeFilesConstraints]
  implicit val envelopeFormat: Format[Envelope] = Json.format[Envelope]
  implicit val envelopeOFormat: OFormat[Envelope] = new OFormat[Envelope] {
    def reads(json: JsValue): JsResult[Envelope] = envelopeFormat.reads(json)
    def writes(o: Envelope): JsObject = envelopeFormat.writes(o).as[JsObject]
  }

  def fromJson(json: JsValue, _id: EnvelopeId): Envelope = {
    val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id)
    Json.fromJson[Envelope](rawData).fold(
      invalid => throw new RuntimeException(s"Invalid json: $invalid"),
      valid => valid
    )
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
case object EnvelopeStatusRouteRequested extends EnvelopeStatus {
  override val name: String = "ROUTE_REQUESTED"
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
  def reads(value: JsValue) =
    EnvelopeStatusTransformer.fromName(value.as[String]).fold[JsResult[EnvelopeStatus]](JsError("Invalid EnvelopeStatus"))(JsSuccess(_))
}

object EnvelopeStatusTransformer {
  def fromName(name: String): Option[EnvelopeStatus] =
    name match {
      case EnvelopeStatusOpen.name           => Some(EnvelopeStatusOpen)
      case EnvelopeStatusSealed.name         => Some(EnvelopeStatusSealed)
      case EnvelopeStatusRouteRequested.name => Some(EnvelopeStatusRouteRequested)
      case EnvelopeStatusClosed.name         => Some(EnvelopeStatusClosed)
      case EnvelopeStatusDeleted.name        => Some(EnvelopeStatusDeleted)
      case _                                 => None
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

case object FileStatusInfected extends FileStatus {
  override val name = "INFECTED"
}

case object FileStatusError extends FileStatus {
  override val name = "UnKnownFileStatusERROR"
}

object FileStatusWrites extends Writes[FileStatus] {
  def writes(c: FileStatus): JsValue = Json.toJson(c.name)
}

object SizeWrites extends Writes[Size] {
  def writes(s: Size): JsValue = Json.toJson(s.toString)
}

object SizeReads extends Reads[Size] {
  def reads(value: JsValue) = JsSuccess(Size(value.as[String]).right.get)
}

object FileStatusReads extends Reads[FileStatus] {
  def reads(value: JsValue) = value.as[String] match {
    case FileStatusQuarantined.name => JsSuccess(FileStatusQuarantined)
    case FileStatusCleaned.name => JsSuccess(FileStatusCleaned)
    case FileStatusAvailable.name => JsSuccess(FileStatusAvailable)
    case FileStatusInfected.name => JsSuccess(FileStatusInfected)
    case FileStatusError.name => JsSuccess(FileStatusError)
  }
}
