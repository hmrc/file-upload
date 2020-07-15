/*
 * Copyright 2020 HM Revenue & Customs
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

import cats.data.Xor
import uk.gov.hmrc.fileupload.controllers.EnvelopeFilesConstraints
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.CanResult
import uk.gov.hmrc.fileupload.write.infrastructure.{EventData, Handler}
import uk.gov.hmrc.fileupload.{FileId, FileRefId}

object EnvelopeHandler {
  type CanResult = Xor[EnvelopeCommandNotAccepted, Unit.type]
  type ContentTypes = String
}

class EnvelopeHandler(envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration) extends Handler[EnvelopeCommand, Envelope] {

  val acceptedConstraints: EnvelopeFilesConstraints = envelopeConstraintsConfigure.acceptedEnvelopeConstraints

  override def handle: PartialFunction[(EnvelopeCommand, Envelope), Xor[EnvelopeCommandNotAccepted, List[EventData]]] = {
    case (command: CreateEnvelope, envelope: Envelope) =>
        envelope.canCreate match {
        case Xor.Left(error) => error
        case Xor.Right(_) => command.constraints match {
          case Some(value) => envelope.canCreateWithFilesCapacityAndSizeAndContentTypes(value, acceptedConstraints).map(_ =>
            EnvelopeCreated(command.id, command.callbackUrl, command.expiryDate, command.metadata, Some(value)))
          case _ => EnvelopeCreated(command.id, command.callbackUrl, command.expiryDate, command.metadata, Some(acceptedConstraints))
        }
      }

    case (command: QuarantineFile, envelope: Envelope) =>
      envelope.canQuarantine(command.fileId, command.fileRefId, command.name).map(_ =>
        FileQuarantined(
          id = command.id, fileId = command.fileId, fileRefId = command.fileRefId,
          created = command.created, name = command.name, contentType = command.contentType, length = command.length, metadata = command.metadata)
      )

    case (command: MarkFileAsClean, envelope: Envelope) =>
      envelope.canMarkFileAsCleanOrInfected(command.fileId, command.fileRefId).map(_ =>
        NoVirusDetected(command.id, command.fileId, command.fileRefId)
      )

    case (command: MarkFileAsInfected, envelope: Envelope) =>
      envelope.canMarkFileAsCleanOrInfected(command.fileId, command.fileRefId).map(_ =>
        VirusDetected(command.id, command.fileId, command.fileRefId)
      )

    case (command: StoreFile, envelope: Envelope) =>
      envelope.canStoreFile(command.fileId, command.fileRefId).map { _ =>
        val fileStored = FileStored(command.id, command.fileId, command.fileRefId, command.length)

        if (withEvent(envelope, fileStored).canRequestRoute.isRight) {
          fileStored And EnvelopeRouteRequested(command.id)
        } else {
          fileStored
        }
      }

    case (command: DeleteFile, envelope: Envelope) =>
      envelope.canDeleteFile(command.fileId).map(_ => FileDeleted(command.id, command.fileId))

    case (command: DeleteEnvelope, envelope: Envelope) =>
      envelope.canDelete.map(_ => EnvelopeDeleted(command.id))

    case (command: SealEnvelope, envelope: Envelope) =>
      envelope.canSeal(command.destination, envelope.constraints.getOrElse(acceptedConstraints)).map { _ =>
        val envelopeSealed = EnvelopeSealed(command.id, command.routingRequestId, command.destination, command.application)

        if (withEvent(envelope, envelopeSealed).canRequestRoute.isRight) {
          envelopeSealed And EnvelopeRouteRequested(command.id)
        } else {
          envelopeSealed
        }
      }

    case (command: UnsealEnvelope, envelope: Envelope) =>
      envelope.canUnseal.map(_ => EnvelopeUnsealed(command.id))

    case (command: MarkEnvelopeAsRouted, envelope: Envelope) =>
      envelope.canRoute.map(_ => EnvelopeRouted(command.id, command.isPushed))

    case (command: ArchiveEnvelope, envelope: Envelope) =>
      envelope.canArchive.map(_ => EnvelopeArchived(command.id))
  }

  override def on: PartialFunction[(Envelope, EventData), Envelope] = {
    case (envelope: Envelope, e: EnvelopeCreated) =>
      envelope.copy(state = Open, constraints = e.constraints)

    case (envelope: Envelope, e: FileQuarantined) =>
      envelope.copy(files = envelope.files + (e.fileId -> QuarantinedFile(e.fileRefId, e.fileId, e.name, e.length.getOrElse(0L))))

    case (envelope: Envelope, e: NoVirusDetected) =>
      envelope.copy(files = envelope.files + (e.fileId -> CleanedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, e.length)))

    case (envelope: Envelope, e: VirusDetected) =>
      envelope.copy(files = envelope.files + (e.fileId -> InfectedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, e.length)))

    case (envelope: Envelope, e: FileDeleted) =>
      envelope.copy(files = envelope.files - e.fileId)

    case (envelope: Envelope, e: FileStored) =>
      envelope.copy(files = envelope.files + (e.fileId -> StoredFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, e.length)))

    case (envelope: Envelope, e: EnvelopeDeleted) =>
      envelope.copy(state = Deleted)

    case (envelope: Envelope, e: EnvelopeSealed) =>
      envelope.copy(state = Sealed)

    case (envelope: Envelope, e: EnvelopeUnsealed) =>
      envelope.copy(state = Open)

    case (envelope: Envelope, e: EnvelopeRouteRequested) =>
      envelope.copy(state = RouteRequested)

    case (envelope: Envelope, e: EnvelopeRouted) =>
      envelope.copy(state = Routed)

    case (envelope: Envelope, e: EnvelopeArchived) =>
      envelope.copy(state = Archived)
  }

  private def withEvent(envelope: Envelope, envelopeEvent: EnvelopeEvent): Envelope =
    on.applyOrElse((envelope, envelopeEvent), (input: (Envelope, EventData)) => envelope)

  import scala.language.implicitConversions

  implicit def EventDataToListEventData(event: EventData): List[EventData] =
    List(event)

  implicit def EventDataToXorRight(event: EventData): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.right(List(event))

  implicit def EventsDataToXorRight(events: List[EventData]): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.right(events)

  implicit def CommandNotAcceptedToXorLeft(error: EnvelopeCommandNotAccepted): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.left(error)

  implicit class AddEventDataToList(item: EventData) {
    def And(another: EventData) = List(item, another)
  }

  implicit class AddEventDataListToList(items: List[EventData]) {
    def And(another: EventData): List[EventData] = items :+ another
  }

}

case class Envelope(
  files            : Map[FileId, File]                = Map.empty,
  state            : State                            = NotCreated,
  constraints      : Option[EnvelopeFilesConstraints] = None
) {

  def canCreate: CanResult = state.canCreate

  def canCreateWithFilesCapacityAndSizeAndContentTypes(userConstraints: EnvelopeFilesConstraints,
                                                       maxLimitConstrains: EnvelopeFilesConstraints): CanResult = {
    state.canCreateWithFilesCapacityAndSizeAndContentTypes(userConstraints, maxLimitConstrains)
  }

  def canDeleteFile(fileId: FileId): CanResult = state.canDeleteFile(fileId, files)

  def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String): CanResult = state.canQuarantine(fileId, fileRefId, name, files)

  def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId): CanResult = state.canMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  def canStoreFile(fileId: FileId, fileRefId: FileRefId): CanResult = state.canStoreFile(fileId, fileRefId, files)

  def canSeal(destination: String, envelopeConstraints: EnvelopeFilesConstraints): CanResult = state.canSeal(files.values.toSeq, destination, envelopeConstraints)

  def canUnseal: CanResult = state.canUnseal

  def canDelete: CanResult = state.canDelete

  def canRequestRoute: CanResult = state.canRequestRoute(files.values.toSeq)

  def canAttemptRouting: CanResult = state.canAttemptRouting

  def canRoute: CanResult = state.canRoute

  def canArchive: CanResult = state.canArchive
}

object State {
  val successResult = Xor.right(Unit)
  val envelopeNotFoundError = Xor.left(EnvelopeNotFoundError)
  val envelopeAlreadyCreatedError = Xor.left(EnvelopeAlreadyCreatedError)
  val envelopeMaxSizeExceededError = Xor.left(InvalidMaxSizeConstraintError)
  val envelopeMaxSizePerItemExceededError = Xor.left(InvalidMaxSizePerItemConstraintError)
  val envelopeMaxItemCountExceededError = Xor.left(InvalidMaxItemCountConstraintError)
  val fileNotFoundError = Xor.left(FileNotFoundError)
  val envelopeSealedError = Xor.left(EnvelopeSealedError)
  val envelopeAlreadyArchivedError = Xor.left(EnvelopeArchivedError)
  val envelopeRoutingAlreadyRequestedError = Xor.left(EnvelopeRoutingAlreadyRequestedError)
  val fileAlreadyProcessedError = Xor.left(FileAlreadyProcessed)
}

sealed trait State {

  import State._

  def canCreate: CanResult = envelopeAlreadyCreatedError

  def canCreateWithFilesCapacityAndSizeAndContentTypes(userConstraints: EnvelopeFilesConstraints,
                                                       maxLimitConstrains: EnvelopeFilesConstraints): CanResult = genericError

  def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult = genericError

  def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String, files: Map[FileId, File]): CanResult = genericError

  def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult = genericError

  def canStoreFile(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult = genericError

  def canSeal(files: Seq[File], destination: String, userConstraints: EnvelopeFilesConstraints): CanResult = genericError

  def canUnseal: CanResult = genericError

  def canDelete: CanResult = genericError

  def canRequestRoute(files: Seq[File]): CanResult = genericError

  def canAttemptRouting: CanResult = genericError

  def canRoute: CanResult = genericError

  def canArchive: CanResult = genericError

  def genericError: CanResult = envelopeNotFoundError // confusing when it says envelope not found, because the envelope was in the wrong state...

  def checkCanMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    files.get(fileId).filter(_.isSame(fileRefId)).map(f =>
      if (!f.isScanned && !f.isAvailable) {
        successResult
      } else {
        Xor.left(FileAlreadyProcessed)
      }).getOrElse(fileNotFoundError)

  def checkCanStoreFile(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    files.get(fileId).filter(_.isSame(fileRefId)).map(f =>
      if (!f.hasError) {
        if (!f.isScanned) {
          fileNotFoundError
        } else if (f.isAvailable) {
          Xor.left(FileAlreadyProcessed)
        } else {
          successResult
        }
      } else {
        Xor.left(FileWithError)
      }
    ).getOrElse(fileNotFoundError)


  def isValidSize(size: Long, acceptedSize: Long): Boolean = {
    (size <= acceptedSize) && (size > 0)
  }
}

object NotCreated extends State {

  import State._

  override def canCreate: CanResult =
    successResult

  override def canCreateWithFilesCapacityAndSizeAndContentTypes(userConstraints: EnvelopeFilesConstraints, maxLimitConstraints: EnvelopeFilesConstraints): CanResult = {
    if (userConstraints.maxItems > maxLimitConstraints.maxItems || userConstraints.maxItems < 1) envelopeMaxItemCountExceededError
    else if (!isValidSize(userConstraints.maxSizeInBytes, maxLimitConstraints.maxSizeInBytes)) envelopeMaxSizeExceededError
    else if (!isValidSize(userConstraints.maxSizePerItemInBytes, maxLimitConstraints.maxSizePerItemInBytes)) envelopeMaxSizePerItemExceededError
    else successResult
  }
}

object Open extends State {

  import State._

  override def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult =
    files.get(fileId).map(_ => successResult).getOrElse(fileNotFoundError)

  // Could be useful in the future (should we check for name duplicates):
  // files.find(f => f.fileId != fileId && f.name == name).map(f => Xor.Left(FileNameDuplicateError(f.fileId))).getOrElse(successResult)
  override def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String, files: Map[FileId, File]): CanResult =
  files.get(fileId).filter(_.isSame(fileRefId)).map(_ => Xor.left(FileAlreadyProcessed)).getOrElse(successResult)

  override def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  override def canStoreFile(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanStoreFile(fileId, fileRefId, files)

  override def canDelete: CanResult = successResult

  override def canSeal(files: Seq[File], destination: String, constraints: EnvelopeFilesConstraints): CanResult = {
    val filesWithError = files.filter(_.hasError)
    if (filesWithError.nonEmpty) {
      Xor.Left(FilesWithError(filesWithError.map(_.fileId)))
    }
    else if (files.size > constraints.maxItems) {
      Xor.Left(EnvelopeItemCountExceededError(constraints.maxItems, files.size))
    }
    else if (files.map(_.fileLength).sum > constraints.maxSizeInBytes) {
      Xor.Left(EnvelopeMaxSizeExceededError(constraints.maxSizeInBytes))
    }
    else {
      successResult
    }
  }
}

object Deleted extends State

object Sealed extends State {

  import State._

  override def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  override def canStoreFile(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanStoreFile(fileId, fileRefId, files)

  override def canUnseal: CanResult = successResult

  override def canRequestRoute(files: Seq[File]): CanResult = {
    val filesNotAvailable = files.filter(!_.isAvailable)
    if (filesNotAvailable.isEmpty) {
      successResult
    } else {
      Xor.Left(FilesNotAvailableError(filesNotAvailable.map(_.fileId)))
    }
  }

  override def genericError: CanResult = envelopeSealedError
}

object RouteRequested extends State {
  import State._

  override def canRoute: CanResult = successResult

  override def canAttemptRouting: CanResult = successResult

  override def genericError: CanResult = envelopeRoutingAlreadyRequestedError
}

object Routed extends State {

  import State._

  override def canArchive: CanResult = successResult

  override def genericError: CanResult = envelopeRoutingAlreadyRequestedError // TODO new one?
}

object Archived extends State {

  import State._

  override def genericError: CanResult = envelopeAlreadyArchivedError
}

trait File {
  def fileRefId: FileRefId

  def fileId: FileId

  def name: String

  def fileLength: Long

  def isSame(otherFileRefId: FileRefId): Boolean =
    fileRefId == otherFileRefId

  def hasError: Boolean = false

  def isScanned: Boolean = false

  def isAvailable: Boolean = false
}

case class QuarantinedFile(fileRefId: FileRefId, fileId: FileId, name: String, fileLength: Long) extends File

case class CleanedFile(fileRefId: FileRefId, fileId: FileId, name: String, fileLength: Long) extends File {
  override val isScanned: Boolean = true
}

case class InfectedFile(fileRefId: FileRefId, fileId: FileId, name: String, fileLength: Long) extends File {
  override val isScanned: Boolean = true
  override val hasError: Boolean = true
}

case class StoredFile(fileRefId: FileRefId, fileId: FileId, name: String, fileLength: Long) extends File {
  override val isScanned: Boolean = true
  override val isAvailable: Boolean = true
}
