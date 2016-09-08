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

import scala.util.Try

//commands

sealed trait EnvelopeCommand extends Command {
  def id: EnvelopeId

  def streamId: StreamId = StreamId(id.toString)
}

case class CreateEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class QurantineFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId,
                         name: String, contentType: String, metadata: JsObject) extends EnvelopeCommand

case class CleanFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class InfectFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class StoreFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId, length: Long) extends EnvelopeCommand

case class SealEnvelope(id: EnvelopeId) extends EnvelopeCommand

//events

case class EnvelopeCreated(id: EnvelopeId)

case class FileQuarantined(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId,
                           name: String, contentType: String, metadata: JsObject)

case class FileCleaned(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileInfected(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileStored(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId, length: Long)

case class EnvelopeSealed(id: EnvelopeId)

object Formatters {
  implicit val envelopeCreatedFormat: Format[EnvelopeCreated] = Json.format[EnvelopeCreated]
  implicit val fileQuarantinedFormat: Format[FileQuarantined] = Json.format[FileQuarantined]
  implicit val fileCleanedFormat: Format[FileCleaned] = Json.format[FileCleaned]
  implicit val fileInfectedFormat: Format[FileInfected] = Json.format[FileInfected]
  implicit val fileStoredFormat: Format[FileStored] = Json.format[FileStored]
  implicit val envelopeSealedFormat: Format[EnvelopeSealed] = Json.format[EnvelopeSealed]
}

object EventSerializer {

  import Formatters._

  def toEventData(eventType: EventType, value: JsValue): EventData =
    eventType.value match {
      case "uk.gov.hmrc.fileupload.write.envelope.EnvelopeCreated" => Json.fromJson[EnvelopeCreated](value).get
      case "uk.gov.hmrc.fileupload.write.envelope.FileQuarantined"=> Json.fromJson[FileQuarantined](value).get
      case "uk.gov.hmrc.fileupload.write.envelope.FileCleaned" => Json.fromJson[FileCleaned](value).get
      case "uk.gov.hmrc.fileupload.write.envelope.FileInfected" => Json.fromJson[FileInfected](value).get
      case "uk.gov.hmrc.fileupload.write.envelope.FileStored" => Json.fromJson[FileStored](value).get
      case "uk.gov.hmrc.fileupload.write.envelope.EnvelopeSealed" => Json.fromJson[EnvelopeSealed](value).get
    }

  def fromEventData(eventData: EventData): JsValue =
    eventData match {
      case e: EnvelopeCreated => Json.toJson(e)
      case e: FileQuarantined => Json.toJson(e)
      case e: FileCleaned => Json.toJson(e)
      case e: FileInfected => Json.toJson(e)
      case e: FileStored => Json.toJson(e)
      case e: EnvelopeSealed => Json.toJson(e)
    }
}
