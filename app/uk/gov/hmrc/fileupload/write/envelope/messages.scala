/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.write.envelope

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.read.envelope.{SizeReads, SizeWrites}
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

// commands

sealed trait EnvelopeCommand extends Command {
  def id: EnvelopeId

  def streamId: StreamId = StreamId(id.value)
}

case class CreateEnvelope(id: EnvelopeId,
                          callbackUrl: Option[String],
                          expiryDate: Option[DateTime],
                          metadata: Option[JsObject],
                          constraints: Option[EnvelopeFilesConstraints]) extends EnvelopeCommand

case class QuarantineFile(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId,
                          created: Long, name: String, contentType: String, length: Option[Long], metadata: JsObject) extends EnvelopeCommand

case class MarkFileAsClean(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends EnvelopeCommand

case class MarkFileAsInfected(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends EnvelopeCommand

case class DeleteFile(id: EnvelopeId, fileId: FileId) extends EnvelopeCommand

case class StoreFile(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId, length: Long) extends EnvelopeCommand

case class DeleteEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class SealEnvelope(id: EnvelopeId, routingRequestId: String, destination: String, application: String) extends EnvelopeCommand

case class UnsealEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class ArchiveEnvelope(id: EnvelopeId) extends EnvelopeCommand

// events

sealed trait EnvelopeEvent extends EventData {
  def id: EnvelopeId

  def streamId: StreamId = StreamId(id.value)
}

case class EnvelopeCreated(id: EnvelopeId, callbackUrl: Option[String],
                           expiryDate: Option[DateTime], metadata: Option[JsObject],
                           constraints: Option[EnvelopeFilesConstraints]) extends EnvelopeEvent

case class FileQuarantined(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId,
                           created: Long, name: String, contentType: String, length: Option[Long] = None, metadata: JsObject) extends EnvelopeEvent

case class NoVirusDetected(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends EnvelopeEvent

case class VirusDetected(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends EnvelopeEvent

case class FileDeleted(id: EnvelopeId, fileId: FileId) extends EnvelopeEvent

case class FileStored(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId, length: Long) extends EnvelopeEvent

case class EnvelopeDeleted(id: EnvelopeId) extends EnvelopeEvent

case class EnvelopeSealed(id: EnvelopeId, routingRequestId: String, destination: String, application: String) extends EnvelopeEvent

case class EnvelopeUnsealed(id: EnvelopeId) extends EnvelopeEvent

case class EnvelopeRouted(id: EnvelopeId) extends EnvelopeEvent

case class EnvelopeArchived(id: EnvelopeId) extends EnvelopeEvent

object Formatters {
  implicit val unsealEnvelopeFormat: Format[UnsealEnvelope] = Json.format[UnsealEnvelope]
  implicit val storeFileFormat: OFormat[StoreFile] = Json.format[StoreFile]
  implicit val quarantineFileFormat: OFormat[QuarantineFile] = Json.format[QuarantineFile]
  implicit val markFileAsCleanFormat: OFormat[MarkFileAsClean] = Json.format[MarkFileAsClean]
  implicit val markFileAsInfectedFormat: OFormat[MarkFileAsInfected] = Json.format[MarkFileAsInfected]
  implicit val sizeReads: Reads[Size] = SizeReads
  implicit val sizeWrites: Writes[Size] = SizeWrites
  implicit val constraintsFormats: OFormat[EnvelopeFilesConstraints] = Json.format[EnvelopeFilesConstraints]
  implicit val envelopeCreatedFormat: Format[EnvelopeCreated] = Json.format[EnvelopeCreated]
  implicit val fileQuarantinedFormat: Format[FileQuarantined] = Json.format[FileQuarantined]
  implicit val fileNoVirusDetectedFormat: Format[NoVirusDetected] = Json.format[NoVirusDetected]
  implicit val fileVirusDetectedFormat: Format[VirusDetected] = Json.format[VirusDetected]
  implicit val fileDeletedFormat: Format[FileDeleted] = Json.format[FileDeleted]
  implicit val fileStoredFormat: Format[FileStored] = Json.format[FileStored]
  implicit val envelopeDeletedFormat: Format[EnvelopeDeleted] = Json.format[EnvelopeDeleted]
  implicit val envelopeSealedFormat: Format[EnvelopeSealed] = Json.format[EnvelopeSealed]
  implicit val envelopeUnsealedFormat: Format[EnvelopeUnsealed] = Json.format[EnvelopeUnsealed]
  implicit val envelopeRoutedFormat: Format[EnvelopeRouted] = Json.format[EnvelopeRouted]
  implicit val envelopeArchivedFormat: Format[EnvelopeArchived] = Json.format[EnvelopeArchived]
}

object EventSerializer {

  import Formatters._

  private val envelopeCreated = nameOf(EnvelopeCreated.getClass)
  private val fileQuarantined = nameOf(FileQuarantined.getClass)
  private val noVirusDetected = nameOf(NoVirusDetected.getClass)
  private val virusDetected = nameOf(VirusDetected.getClass)
  private val fileDeleted = nameOf(FileDeleted.getClass)
  private val fileStored = nameOf(FileStored.getClass)
  private val envelopeDeleted = nameOf(EnvelopeDeleted.getClass)
  private val envelopeSealed = nameOf(EnvelopeSealed.getClass)
  private val envelopeUnsealed = nameOf(EnvelopeUnsealed.getClass)
  private val envelopeRouted = nameOf(EnvelopeRouted.getClass)
  private val envelopeArchived = nameOf(EnvelopeArchived.getClass)

  private def nameOf(clazz: Class[_]) =
    clazz.getName.replace("$", "")

  def toEventData(eventType: EventType, value: JsValue): EventData =
    eventType.value match {
      case `envelopeCreated` => Json.fromJson[EnvelopeCreated](value).get
      case `fileQuarantined` => Json.fromJson[FileQuarantined](value).get
      case `noVirusDetected` => Json.fromJson[NoVirusDetected](value).get
      case `virusDetected` => Json.fromJson[VirusDetected](value).get
      case `fileDeleted` => Json.fromJson[FileDeleted](value).get
      case `fileStored` => Json.fromJson[FileStored](value).get
      case `envelopeDeleted` => Json.fromJson[EnvelopeDeleted](value).get
      case `envelopeSealed` => Json.fromJson[EnvelopeSealed](value).get
      case `envelopeUnsealed` => Json.fromJson[EnvelopeUnsealed](value).get
      case `envelopeRouted` => Json.fromJson[EnvelopeRouted](value).get
      case `envelopeArchived` => Json.fromJson[EnvelopeArchived](value).get
    }

  def fromEventData(eventData: EventData): JsValue =
    eventData match {
      case e: EnvelopeCreated => Json.toJson(e)
      case e: FileQuarantined => Json.toJson(e)
      case e: NoVirusDetected => Json.toJson(e)
      case e: VirusDetected => Json.toJson(e)
      case e: FileDeleted => Json.toJson(e)
      case e: FileStored => Json.toJson(e)
      case e: EnvelopeDeleted => Json.toJson(e)
      case e: EnvelopeSealed => Json.toJson(e)
      case e: EnvelopeUnsealed => Json.toJson(e)
      case e: EnvelopeRouted => Json.toJson(e)
      case e: EnvelopeArchived => Json.toJson(e)
    }

  val eventWrite = new Writes[Event] {
    def writes(event: Event) = Json.obj(
      "eventId" -> event.eventId.value,
      "streamId" -> event.streamId.value,
      "version" -> event.version.value,
      "created" -> event.created.value,
      "eventType" -> event.eventType.value,
      "eventData" -> fromEventData(event.eventData)
    )
  }
}

// error

sealed abstract class EnvelopeCommandNotAccepted extends CommandNotAccepted

case object EnvelopeNotFoundError extends EnvelopeCommandNotAccepted

case object EnvelopeAlreadyCreatedError extends EnvelopeCommandNotAccepted

sealed trait EnvelopeInvalidConstraintError extends EnvelopeCommandNotAccepted

case object InvalidMaxSizeConstraintError extends EnvelopeInvalidConstraintError {
  override def toString = s"constraints.maxSize exceeds maximum allowed value"
}

case object InvalidMaxSizePerItemConstraintError extends EnvelopeInvalidConstraintError {
  override def toString = s"constraints.maxSizePerItem exceeds maximum allowed value"
}

case object InvalidMaxItemCountConstraintError extends EnvelopeInvalidConstraintError {
  override def toString = s"constraints.maxItems error"
}

case object EnvelopeSealedError extends EnvelopeCommandNotAccepted

case object FileWithError extends EnvelopeCommandNotAccepted

case class FilesWithError(fileIds: Seq[FileId]) extends EnvelopeCommandNotAccepted

case class EnvelopeItemCountExceededError(allowedItemCount: Int, actualItemCount: Int) extends EnvelopeCommandNotAccepted
case class EnvelopeMaxSizeExceededError(maxSizeAllowed: Long) extends EnvelopeCommandNotAccepted

case class FilesNotAvailableError(fileIds: Seq[FileId]) extends EnvelopeCommandNotAccepted

case class FileNameDuplicateError(fileId: FileId) extends EnvelopeCommandNotAccepted

case object FileNotFoundError extends EnvelopeCommandNotAccepted

case object FileAlreadyProcessed extends EnvelopeCommandNotAccepted

case object EnvelopeArchivedError extends EnvelopeCommandNotAccepted

case object EnvelopeAlreadyRoutedError extends EnvelopeCommandNotAccepted
