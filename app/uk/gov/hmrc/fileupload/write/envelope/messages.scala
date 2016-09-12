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

package uk.gov.hmrc.fileupload.write.envelope

import play.api.libs.json.{Format, JsObject, JsValue, Json}
import uk.gov.hmrc.fileupload.domain.{Command, EventData, EventType, StreamId}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}

//commands

sealed trait EnvelopeCommand extends Command {
  def id: EnvelopeId

  def streamId: StreamId = StreamId(id.toString)
}

case class CreateEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class QurantineFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId,
                         name: String, contentType: String, metadata: JsObject) extends EnvelopeCommand

case class MarkFileAsClean(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class MarkFileAsInfected(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class DeleteFile(id: EnvelopeId, fileId: FileId) extends EnvelopeCommand

case class StoreFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId, length: Long) extends EnvelopeCommand

case class DeleteEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class ArchiveEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class SealEnvelope(id: EnvelopeId) extends EnvelopeCommand

//events

case class EnvelopeCreated(id: EnvelopeId)

case class FileQuarantined(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId,
                           name: String, contentType: String, metadata: JsObject)

case class NoVirusDetected(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class VirusDetected(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileDeleted(id: EnvelopeId, fileId: FileId)

case class FileStored(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId, length: Long)

case class EnvelopeDeleted(id: EnvelopeId)

case class EnvelopeArchived(id: EnvelopeId)

case class EnvelopeSealed(id: EnvelopeId)

case class EnvelopeRouted(id: EnvelopeId)

object Formatters {
  implicit val envelopeCreatedFormat: Format[EnvelopeCreated] = Json.format[EnvelopeCreated]
  implicit val fileQuarantinedFormat: Format[FileQuarantined] = Json.format[FileQuarantined]
  implicit val fileNoVirusDetectedFormat: Format[NoVirusDetected] = Json.format[NoVirusDetected]
  implicit val fileVirusDetectedFormat: Format[VirusDetected] = Json.format[VirusDetected]
  implicit val fileDeletedFormat: Format[FileDeleted] = Json.format[FileDeleted]
  implicit val fileStoredFormat: Format[FileStored] = Json.format[FileStored]
  implicit val envelopeDeletedFormat: Format[EnvelopeDeleted] = Json.format[EnvelopeDeleted]
  implicit val envelopeArchivedFormat: Format[EnvelopeArchived] = Json.format[EnvelopeArchived]
  implicit val envelopeSealedFormat: Format[EnvelopeSealed] = Json.format[EnvelopeSealed]
  implicit val envelopeRoutedFormat: Format[EnvelopeRouted] = Json.format[EnvelopeRouted]
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
  private val envelopeArchived = nameOf(EnvelopeArchived.getClass)
  private val envelopeSealed = nameOf(EnvelopeSealed.getClass)
  private val envelopeRouted = nameOf(EnvelopeRouted.getClass)

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
      case `envelopeArchived`  => Json.fromJson[EnvelopeArchived](value).get
      case `envelopeSealed` => Json.fromJson[EnvelopeSealed](value).get
      case `envelopeRouted` => Json.fromJson[EnvelopeRouted](value).get
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
      case e: EnvelopeArchived => Json.toJson(e)
      case e: EnvelopeSealed => Json.toJson(e)
      case e: EnvelopeRouted => Json.toJson(e)
    }
}
