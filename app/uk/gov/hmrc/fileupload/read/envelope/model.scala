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

package uk.gov.hmrc.fileupload.read.envelope

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.write.infrastructure.Version
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileName, FileRefId}

case class Envelope(
  _id        : EnvelopeId                       = EnvelopeId(),
  version    : Version                          = Version(0),
  status     : EnvelopeStatus                   = EnvelopeStatus.EnvelopeStatusOpen,
  constraints: Option[EnvelopeFilesConstraints] = None,
  callbackUrl: Option[String]                   = None,
  expiryDate : Option[DateTime]                 = None,
  metadata   : Option[JsObject]                 = None,
  files      : Option[Seq[File]]                = None,
  destination: Option[String]                   = None,
  application: Option[String]                   = None,
  isPushed   : Option[Boolean]                  = None,
  lastPushed : Option[DateTime]                 = None,
  reason     : Option[String]                   = None
):
  def getFileById(fileId: FileId): Option[File] =
    files.flatMap(_.find(_.fileId == fileId))

case class File(
  fileId     : FileId,
  fileRefId  : FileRefId,
  status     : FileStatus,
  name       : Option[FileName] = None,
  contentType: Option[String]   = None,
  length     : Option[Long]     = None,
  uploadDate : Option[DateTime] = None,
  revision   : Option[Int]      = None,
  metadata   : Option[JsObject] = None,
  rel        : Option[String]   = Some("file")
)

object Envelope:
  given envelopeFormat: Format[Envelope] =
    given Reads[DateTime]                   = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
    given Writes[DateTime]                  = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
    given Reads[FileStatus]                 = FileStatusReads
    given Writes[FileStatus]                = FileStatusWrites
    given Format[File]                      = { given Format[FileName] = FileName.apiFormat
                                                Json.format[File]
                                              }
    given Reads[EnvelopeStatus]             = EnvelopeStatusReads
    given Writes[EnvelopeStatus]            = EnvelopeStatusWrites
    given Reads[Size]                       = SizeReads
    given Writes[Size]                      = SizeWrites
    given OFormat[EnvelopeFilesConstraints] = Json.format[EnvelopeFilesConstraints]

    Json.format[Envelope]

  def fromJson(json: JsValue, _id: EnvelopeId): Envelope =
    val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id)
    Json.fromJson[Envelope](rawData).fold(
      invalid => throw RuntimeException(s"Invalid json: $invalid"),
      valid   => valid
    )

case class ValidationException(reason: String) extends IllegalArgumentException(reason)

enum EnvelopeStatus(
  val name: String
):
  // TODO drop EnvelopeStatus prefix from names (we have enum namespace)
  case EnvelopeStatusOpen           extends EnvelopeStatus("OPEN")
  case EnvelopeStatusSealed         extends EnvelopeStatus("SEALED")
  case EnvelopeStatusRouteRequested extends EnvelopeStatus("ROUTE_REQUESTED")
  case EnvelopeStatusClosed         extends EnvelopeStatus("CLOSED")
  case EnvelopeStatusDeleted        extends EnvelopeStatus("DELETED")

object EnvelopeStatusWrites extends Writes[EnvelopeStatus] {
  override def writes(c: EnvelopeStatus): JsValue =
    Json.toJson(c.name)
}

object EnvelopeStatusReads extends Reads[EnvelopeStatus] {
  override def reads(value: JsValue) =
    EnvelopeStatusTransformer.fromName(value.as[String]).fold[JsResult[EnvelopeStatus]](JsError("Invalid EnvelopeStatus"))(JsSuccess(_))
}

object EnvelopeStatusTransformer {
  def fromName(name: String): Option[EnvelopeStatus] =
    name match {
      case EnvelopeStatus.EnvelopeStatusOpen.name           => Some(EnvelopeStatus.EnvelopeStatusOpen)
      case EnvelopeStatus.EnvelopeStatusSealed.name         => Some(EnvelopeStatus.EnvelopeStatusSealed)
      case EnvelopeStatus.EnvelopeStatusRouteRequested.name => Some(EnvelopeStatus.EnvelopeStatusRouteRequested)
      case EnvelopeStatus.EnvelopeStatusClosed.name         => Some(EnvelopeStatus.EnvelopeStatusClosed)
      case EnvelopeStatus.EnvelopeStatusDeleted.name        => Some(EnvelopeStatus.EnvelopeStatusDeleted)
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
  override def writes(c: FileStatus): JsValue = Json.toJson(c.name)
}

object SizeWrites extends Writes[Size] {
  override def writes(s: Size): JsValue = Json.toJson(s.toString)
}

object SizeReads extends Reads[Size] {
  override def reads(value: JsValue) =
    Size(value.as[String]) match {
      case Right(size)   => JsSuccess(size)
      case Left(failure) => JsError(failure.message)
    }
}

object FileStatusReads extends Reads[FileStatus] {
  override def reads(value: JsValue) = value.as[String] match {
    case FileStatusQuarantined.name => JsSuccess(FileStatusQuarantined)
    case FileStatusCleaned.name     => JsSuccess(FileStatusCleaned)
    case FileStatusAvailable.name   => JsSuccess(FileStatusAvailable)
    case FileStatusInfected.name    => JsSuccess(FileStatusInfected)
    case FileStatusError.name       => JsSuccess(FileStatusError)
  }
}
