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

package uk.gov.hmrc.fileupload.envelope

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsObject, _}
import uk.gov.hmrc.fileupload.envelope.Service.UploadedFileInfo
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class Envelope(_id: EnvelopeId = EnvelopeId(),
                    status: EnvelopeStatus = EnvelopeStatusOpen,
                    constraints: Option[Constraints] = None,
                    callbackUrl: Option[String] = None, expiryDate: Option[DateTime] = None,
                    metadata: Option[Map[String, JsValue]] = None, files: Option[Seq[File]] = None) {

  require(!isExpired, "expiry date cannot be in the past")

  def isExpired: Boolean = expiryDate.exists(_.isBeforeNow)

  def addFile(uploadedFileInfo: UploadedFileInfo) = {
    val file = files.flatMap(_.collectFirst {
      case f if f.fileId == uploadedFileInfo.fileId =>
        f.copy(fsReference = Some(uploadedFileInfo.fsReference),
          length = Some(uploadedFileInfo.length),
          uploadDate = uploadedFileInfo.uploadDate.map(new DateTime(_)))
    }).getOrElse(File(fileId = uploadedFileInfo.fileId, fsReference = Some(uploadedFileInfo.fsReference),
      length = Some(uploadedFileInfo.length),
      uploadDate = uploadedFileInfo.uploadDate.map(new DateTime(_))))

    add(file)
  }

  def addMetadataToAFile(fileId: FileId, name: Option[String] = None, contentType: Option[String] = None,
                         revision: Option[Int] = None, metadata: Option[JsObject] = None): Envelope = {

    val file = files.flatMap(_.collectFirst {
      case f if f.fileId == fileId =>
        f.copy(name = name, contentType = contentType, metadata = metadata)
    }).getOrElse(File(fileId = fileId, name = name, contentType = contentType, metadata = metadata))

    add(file)
  }

  private def add(file: File): Envelope = {
    val maybeFiles: Option[Seq[File]] = files.map( _.filterNot( _.fileId == file.fileId) )
    val newFiles = maybeFiles.getOrElse(Seq.empty[File]) :+ file
    copy(files = Some(newFiles))
  }

  def getFileById(fileId: FileId): Option[File] = {
    files.flatMap { _.find { file => file.fileId == fileId }}
  }
}

case class Constraints(contentTypes: Option[Seq[String]] = None,
                       maxItems: Option[Int] = None,
                       maxSize: Option[String] = None,
                       maxSizePerItem: Option[String] = None) {

  maxSize.foreach(validateSizeFormat("maxSize", _))
  maxSizePerItem.foreach(validateSizeFormat("maxSizePerItem", _))

  def validateSizeFormat(name: String, value: String) = {
    val pattern = "[0-9]+(KB|MB|GB|TB|PB)".r
    if (pattern.findFirstIn(value).isEmpty) throw ValidationException(s"$name has an invalid size format ($value)")
  }
}

case class File(fileId: FileId,
                fsReference: Option[FileId] = None,
                name: Option[String] = None,
                contentType: Option[String] = None,
                length: Option[Long] = None,
                uploadDate: Option[DateTime] = None,
                revision: Option[Int] = None,
                metadata: Option[JsObject] = None,
                rel: String = "file", href: Option[String] = None)

object Envelope {

  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileStatusReads: Reads[FileStatus] = FileStatusReads
  implicit val fileStatusWrites: Writes[FileStatus] = FileStatusWrites
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val envelopeStatusReads: Reads[EnvelopeStatus] = EnvelopeStatusReads
  implicit val envelopeStatusWrites: Writes[EnvelopeStatus] = EnvelopeStatusWrites
  implicit val constraintsReads: Format[Constraints] = Json.format[Constraints]
  implicit val envelopeReads: Format[Envelope] = Json.format[Envelope]

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
