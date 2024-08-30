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

package uk.gov.hmrc.fileupload.write.envelope

import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.read.envelope.{SizeReads, SizeWrites}
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileName, FileRefId}

// commands

sealed trait EnvelopeCommand extends Command:
  def id: EnvelopeId

  override def streamId: StreamId =
    StreamId(id.value)

case class CreateEnvelope(
  override val id: EnvelopeId,
  callbackUrl    : Option[String],
  expiryDate     : Option[DateTime],
  metadata       : Option[JsObject],
  constraints    : Option[EnvelopeFilesConstraints]
) extends EnvelopeCommand

case class QuarantineFile(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId,
  created        : Long,
  name           : FileName,
  contentType    : String,
  length         : Option[Long],
  metadata       : JsObject
) extends EnvelopeCommand

case class MarkFileAsClean(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId
) extends EnvelopeCommand

case class MarkFileAsInfected(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId
) extends EnvelopeCommand

case class DeleteFile(
  override val id: EnvelopeId,
  fileId         : FileId
) extends EnvelopeCommand

case class StoreFile(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId,
  length         : Long
) extends EnvelopeCommand

case class DeleteEnvelope(
  override val id: EnvelopeId
) extends EnvelopeCommand

case class SealEnvelope(
  override val id : EnvelopeId,
  routingRequestId: String,
  destination     : String,
  application     : String
) extends EnvelopeCommand

case class UnsealEnvelope(
  override val id: EnvelopeId
) extends EnvelopeCommand

case class MarkEnvelopeAsRouteAttempted(
  override val id: EnvelopeId,
  lastPushed : Option[DateTime]
) extends EnvelopeCommand

case class MarkEnvelopeAsRouted(
  override val id: EnvelopeId,
  isPushed   : Boolean,
  reason     : Option[String] = None
) extends EnvelopeCommand

case class ArchiveEnvelope(
  override val id: EnvelopeId,
  reason         : Option[String] = None
) extends EnvelopeCommand

// events

sealed trait EnvelopeEvent extends EventData {
  def id: EnvelopeId

  override def streamId: StreamId =
    StreamId(id.value)
}

case class EnvelopeCreated(
  override val id: EnvelopeId,
  callbackUrl    : Option[String],
  expiryDate     : Option[DateTime],
  metadata       : Option[JsObject],
  constraints    : Option[EnvelopeFilesConstraints]
) extends EnvelopeEvent

case class FileQuarantined(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId,
  created        : Long,
  name           : FileName,
  contentType    : String,
  length         : Option[Long] = None,
  metadata       : JsObject
) extends EnvelopeEvent

case class NoVirusDetected(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId
) extends EnvelopeEvent

case class VirusDetected(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId
) extends EnvelopeEvent

case class FileDeleted(
  override val id: EnvelopeId,
  fileId         : FileId
) extends EnvelopeEvent

case class FileStored(
  override val id: EnvelopeId,
  fileId         : FileId,
  fileRefId      : FileRefId,
  length         : Long
) extends EnvelopeEvent

case class EnvelopeDeleted(
  override val id: EnvelopeId
) extends EnvelopeEvent

case class EnvelopeSealed(
  override val id : EnvelopeId,
  routingRequestId: String,
  destination     : String,
  application     : String
) extends EnvelopeEvent

case class EnvelopeUnsealed(
  override val id: EnvelopeId
) extends EnvelopeEvent

case class EnvelopeRouteRequested(
  override val id: EnvelopeId,
  lastPushed     : Option[DateTime]
) extends EnvelopeEvent

case class EnvelopePushNotNeeded(
  override val id: EnvelopeId
) extends EnvelopeEvent

case class EnvelopeRouted(
  override val id: EnvelopeId,
  isPushed       : Boolean,
  reason         : Option[String]
) extends EnvelopeEvent

case class EnvelopeArchived(
  override val id: EnvelopeId,
  reason         : Option[String]
) extends EnvelopeEvent

object Formatters {
  import play.api.libs.functional.syntax._

  private given Format[FileName]   = FileName.apiFormat

  given Format[UnsealEnvelope]     = Json.format[UnsealEnvelope]
  given Format[StoreFile]          = Json.format[StoreFile]
  given Format[QuarantineFile]     = Json.format[QuarantineFile]
  given Format[MarkFileAsClean]    = Json.format[MarkFileAsClean]
  given Format[MarkFileAsInfected] = Json.format[MarkFileAsInfected]
  private given Format[EnvelopeFilesConstraints] =
    given Reads[Size]  = SizeReads
    given Writes[Size] = SizeWrites
    Json.format[EnvelopeFilesConstraints]
  given Format[EnvelopeCreated] = {
    // We are actually writing the date in mongo as a number. jodaDateReads supports both number and the specified string format.
    // (We could in theory start writing dates with JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'"), to migrate format).
    given Reads[DateTime]  = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
    given Writes[DateTime] = JodaWrites.JodaDateTimeNumberWrites
    Json.format[EnvelopeCreated]
  }
  given Format[FileQuarantined]  = Json.format[FileQuarantined]
  given Format[NoVirusDetected]  = Json.format[NoVirusDetected]
  given Format[VirusDetected]    = Json.format[VirusDetected]
  given Format[FileDeleted]      = Json.format[FileDeleted]
  given Format[FileStored]       = Json.format[FileStored]
  given Format[EnvelopeDeleted]  = Json.format[EnvelopeDeleted]
  given Format[EnvelopeSealed]   = Json.format[EnvelopeSealed]
  given Format[EnvelopeUnsealed] = Json.format[EnvelopeUnsealed]
  given Format[EnvelopeRouteRequested] = {
    // formats used for both API and storing in Mongo
    given Reads[DateTime]  = JodaReads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
    given Writes[DateTime] = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
    Json.format[EnvelopeRouteRequested]
  }

  // for backward compatibility; isPushed may not be present.
  given Format[EnvelopeRouted] =
    ( (__ \ "id"      ).format[EnvelopeId]
    ~ (__ \ "isPushed").formatNullable[Boolean].inmap(_.getOrElse(false), Some.apply[Boolean]) // formatWithDefault not available for this version of play-json
    ~ (__ \ "reason"  ).formatNullable[String]
    )(EnvelopeRouted.apply, er => Tuple.fromProductTyped(er))

  given Format[EnvelopeArchived] =
    Json.format[EnvelopeArchived]
}

object EventSerializer:

  private val envelopeCreated        = nameOf(EnvelopeCreated       .getClass)
  private val fileQuarantined        = nameOf(FileQuarantined       .getClass)
  private val noVirusDetected        = nameOf(NoVirusDetected       .getClass)
  private val virusDetected          = nameOf(VirusDetected         .getClass)
  private val fileDeleted            = nameOf(FileDeleted           .getClass)
  private val fileStored             = nameOf(FileStored            .getClass)
  private val envelopeDeleted        = nameOf(EnvelopeDeleted       .getClass)
  private val envelopeSealed         = nameOf(EnvelopeSealed        .getClass)
  private val envelopeUnsealed       = nameOf(EnvelopeUnsealed      .getClass)
  private val envelopeRouteRequested = nameOf(EnvelopeRouteRequested.getClass)
  private val envelopeRouted         = nameOf(EnvelopeRouted        .getClass)
  private val envelopeArchived       = nameOf(EnvelopeArchived      .getClass)

  private val logger = Logger(getClass)

  private def nameOf(clazz: Class[_]) =
    clazz.getName.replace("$", "")

  def toEventData(eventType: EventType, value: JsValue): EventData =
    import Formatters.given
    val jsResult = eventType.value match
      case `envelopeCreated`        => Json.fromJson[EnvelopeCreated       ](value)
      case `fileQuarantined`        => Json.fromJson[FileQuarantined       ](value)
      case `noVirusDetected`        => Json.fromJson[NoVirusDetected       ](value)
      case `virusDetected`          => Json.fromJson[VirusDetected         ](value)
      case `fileDeleted`            => Json.fromJson[FileDeleted           ](value)
      case `fileStored`             => Json.fromJson[FileStored            ](value)
      case `envelopeDeleted`        => Json.fromJson[EnvelopeDeleted       ](value)
      case `envelopeSealed`         => Json.fromJson[EnvelopeSealed        ](value)
      case `envelopeUnsealed`       => Json.fromJson[EnvelopeUnsealed      ](value)
      case `envelopeRouteRequested` => Json.fromJson[EnvelopeRouteRequested](value)
      case `envelopeRouted`         => Json.fromJson[EnvelopeRouted        ](value)
      case `envelopeArchived`       => Json.fromJson[EnvelopeArchived      ](value)

    jsResult.asEither.left.foreach: errors =>
      logger.error(s"Unable to create eventData of type [${eventType.value}] due to json errors [$errors]")
      logger.info(s"Unable to create eventData of type [${eventType.value}] from json [${Json.stringify(value)}] due to errors [$errors]")

    jsResult.fold(
      invalid => throw RuntimeException(s"Invalid json: $invalid"),
      valid   => valid
    )

  def fromEventData(eventData: EventData): JsValue =
    import Formatters.given
    eventData match
      case e: EnvelopeCreated        => Json.toJson(e)
      case e: FileQuarantined        => Json.toJson(e)
      case e: NoVirusDetected        => Json.toJson(e)
      case e: VirusDetected          => Json.toJson(e)
      case e: FileDeleted            => Json.toJson(e)
      case e: FileStored             => Json.toJson(e)
      case e: EnvelopeDeleted        => Json.toJson(e)
      case e: EnvelopeSealed         => Json.toJson(e)
      case e: EnvelopeUnsealed       => Json.toJson(e)
      case e: EnvelopeRouteRequested => Json.toJson(e)
      case e: EnvelopeRouted         => Json.toJson(e)
      case e: EnvelopeArchived       => Json.toJson(e)

  val eventWrite: Writes[Event] =
    (event: Event) =>
      Json.obj(
        "eventId"   -> event.eventId.value,
        "streamId"  -> event.streamId.value,
        "version"   -> event.version.value,
        "created"   -> event.created.value,
        "eventType" -> event.eventType.value,
        "eventData" -> fromEventData(event.eventData)
      )

end EventSerializer

/**
  * This trait allow us to re-publish the events when a conflicting command is encountered.
  */
trait ConflictingCommand:
  self: EnvelopeCommandNotAccepted =>

sealed abstract class EnvelopeCommandNotAccepted extends CommandNotAccepted

case object EnvelopeNotFoundError extends EnvelopeCommandNotAccepted with ConflictingCommand

case object EnvelopeAlreadyCreatedError extends EnvelopeCommandNotAccepted with ConflictingCommand

sealed trait EnvelopeInvalidConstraintError extends EnvelopeCommandNotAccepted

case object InvalidMaxSizeConstraintError extends EnvelopeInvalidConstraintError:
  override def toString = s"constraints.maxSize exceeds maximum allowed value"

case object InvalidMaxSizePerItemConstraintError extends EnvelopeInvalidConstraintError:
  override def toString = s"constraints.maxSizePerItem exceeds maximum allowed value"

case object InvalidMaxItemCountConstraintError extends EnvelopeInvalidConstraintError:
  override def toString = s"constraints.maxItems error"

case object EnvelopeSealedError extends EnvelopeCommandNotAccepted with ConflictingCommand

case object FileWithError extends EnvelopeCommandNotAccepted

case class FilesWithError(
  fileIds: Seq[FileId]
) extends EnvelopeCommandNotAccepted

case class EnvelopeItemCountExceededError(
  allowedItemCount: Int,
  actualItemCount : Int
) extends EnvelopeCommandNotAccepted

case class EnvelopeMaxSizeExceededError(
  maxSizeAllowed: Long
) extends EnvelopeCommandNotAccepted

case class FilesNotAvailableError(
  fileIds: Seq[FileId]
) extends EnvelopeCommandNotAccepted

case class FileNameDuplicateError(
  fileId: FileId
) extends EnvelopeCommandNotAccepted

case object FileNotFoundError extends EnvelopeCommandNotAccepted

case object FileAlreadyProcessed extends EnvelopeCommandNotAccepted

case object EnvelopeArchivedError extends EnvelopeCommandNotAccepted with ConflictingCommand

case object EnvelopeRoutingAlreadyRequestedError extends EnvelopeCommandNotAccepted with ConflictingCommand

case object EnvelopeAlreadyRoutedError extends EnvelopeCommandNotAccepted with ConflictingCommand
