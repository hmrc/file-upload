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

import cats.data.Xor
import uk.gov.hmrc.fileupload.write.envelope.Envelope.CanResult
import uk.gov.hmrc.fileupload.write.infrastructure.EventData
import uk.gov.hmrc.fileupload.{FileId, FileRefId}

object Envelope {

  type CanResult = Xor[EnvelopeCommandNotAccepted, Unit.type]

  def handle: PartialFunction[(EnvelopeCommand, Envelope), Xor[EnvelopeCommandNotAccepted, List[EventData]]] = {
    case (command: CreateEnvelope, envelope: Envelope) =>
      EnvelopeCreated(command.id, command.callbackUrl)

    case (command: QuarantineFile, envelope: Envelope) =>
      envelope.canQuarantine(command.fileId, command.name).map(_ => List(
        FileQuarantined(
          id = command.id, fileId = command.fileId, fileRefId = command.fileRefId,
          created = command.created, name = command.name, contentType = command.contentType, metadata = command.metadata)
      ))

    case (command: MarkFileAsClean, envelope: Envelope) =>
      if (envelope.hasFileRefId(command.fileId, command.fileRefId)) {
        NoVirusDetected(command.id, command.fileId, command.fileRefId)
      } else {
        FileNotFoundError
      }

    case (command: MarkFileAsInfected, envelope: Envelope) =>
      if (envelope.hasFileRefId(command.fileId, command.fileRefId)) {
        VirusDetected(command.id, command.fileId, command.fileRefId)
      } else {
        FileNotFoundError
      }

    case (command: StoreFile, envelope: Envelope) =>
      // TODO: allow only if file has no virus
      if (envelope.hasFileRefId(command.fileId, command.fileRefId)) {
        val fileStored = FileStored(command.id, command.fileId, command.fileRefId, command.length)

        if (withEvent(envelope, fileStored).canRoute.isRight) {
          List(fileStored, EnvelopeRouted(command.id))
        } else {
          fileStored
        }
      } else {
        FileNotFoundError
      }

    case (command: DeleteFile, envelope: Envelope) =>
      envelope.canDeleteFile(command.fileId).map (_ => List(FileDeleted(command.id, command.fileId)))

    case (command: DeleteEnvelope, envelope: Envelope) =>
      envelope.canDelete.map(_ => List(EnvelopeDeleted(command.id)))

    case (command: SealEnvelope, envelope: Envelope) =>
      envelope.canSeal.map(_ => List(EnvelopeSealed(command.id, command.destination)))

    case (command: ArchiveEnvelope, envelope: Envelope) =>
      envelope.canArchive.map(_ => List(EnvelopeArchived(command.id)))
  }

  def on: PartialFunction[(Envelope, EventData), Envelope] = {
      case (envelope: Envelope, e: EnvelopeCreated) =>
        envelope.copy(state = Open)

      case (envelope: Envelope, e: FileQuarantined) =>
        envelope.copy(files = envelope.files + (e.fileId -> QuarantinedFile(e.fileRefId, e.fileId, e.name)))

      case (envelope: Envelope, e: NoVirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> CleanedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name)))

      case (envelope: Envelope, e: VirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> InfectedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name)))

      case (envelope: Envelope, e: FileDeleted) =>
        envelope.copy(files = envelope.files - e.fileId)

      case (envelope: Envelope, e: FileStored) =>
        envelope.copy(files = envelope.files + (e.fileId -> StoredFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name)))

      case (envelope: Envelope, e: EnvelopeDeleted) =>
        envelope.copy(state = Deleted)

      case (envelope: Envelope, e: EnvelopeSealed) =>
        envelope.copy(state = Sealed)

      case (envelope: Envelope, e: EnvelopeRouted) =>
        envelope.copy(state = Routed)

      case (envelope: Envelope, e: EnvelopeArchived) =>
        envelope.copy(state = Archived)
  }

  private def withEvent(envelope: Envelope, envelopeEvent: EnvelopeEvent): Envelope =
    on.applyOrElse((envelope, envelopeEvent), (input: (Envelope, EventData)) => envelope)

  import scala.language.implicitConversions

  implicit def EventDataToXorRight(event: EventData): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.right(List(event))

  implicit def EventsDataToXorRight(events: List[EventData]): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.right(events)

  implicit def CommandNotAcceptedToXorLeft(error: EnvelopeCommandNotAccepted): Xor[EnvelopeCommandNotAccepted, List[EventData]] =
    Xor.left(error)
}

case class Envelope(files: Map[FileId, File] = Map.empty, state: State = NotCreated) {

  def hasFileRefId(fileId: FileId, fileReferenceId: FileRefId): Boolean =
    files.get(fileId).exists(_.isSame(fileReferenceId))

  def canDeleteFile(fileId: FileId): CanResult = state.canDeleteFile(fileId, files)

  def canQuarantine(fileId: FileId, name: String): CanResult = state.canQuarantine(fileId, name, files.values.toSeq)

  def canSeal: CanResult = state.canSeal(files.values.toSeq)

  def canDelete: CanResult = state.canDelete

  def canRoute: CanResult = state.canRoute(files.values.toSeq)

  def canArchive: CanResult = state.canArchive
}

sealed trait State {

  val successResult = Xor.Right(Unit)
  val envelopeNotFoundError = Xor.Left(EnvelopeNotFoundError)
  val fileNotFoundError = Xor.Left(FileNotFoundError)
  val envelopeSealedError = Xor.Left(EnvelopeSealedError)
  val envelopeAlreadyArchivedError = Xor.Left(EnvelopeArchivedError)

  def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult = genericError

  def canQuarantine(fileId: FileId, name: String, files: Seq[File]): CanResult = genericError

  def canSeal(files: Seq[File]): CanResult = genericError

  def canDelete: CanResult = genericError

  def canRoute(files: Seq[File]): CanResult = genericError

  def canArchive: CanResult = genericError

  def genericError: CanResult = envelopeNotFoundError
}

object NotCreated extends State

object Open extends State {

  override def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult =
    files.get(fileId).map(f => successResult).getOrElse(fileNotFoundError)

  // Could be useful in the future (should we check for name duplicates):
  // files.find(f => f.fileId != fileId && f.name == name).map(f => Xor.Left(FileNameDuplicateError(f.fileId))).getOrElse(successResult)
  override def canQuarantine(fileId: FileId, name: String, files: Seq[File]): CanResult = successResult


  override def canDelete: CanResult = successResult

  override def canSeal(files: Seq[File]): CanResult = {
    val filesWithError = files.filter(_.hasError)
    if (filesWithError.isEmpty) {
      successResult
    } else {
      Xor.Left(FilesWithError(filesWithError.map(_.fileId)))
    }
  }
}

object Deleted extends State

object Sealed extends State {

  override def canRoute(files: Seq[File]): CanResult = {
    val filesNotAvailable = files.filter(!_.isAvailable)
    if (filesNotAvailable.isEmpty) {
      successResult
    } else {
      Xor.Left(FilesNotAvailableError(filesNotAvailable.map(_.fileId)))
    }
  }

  override def genericError: CanResult = envelopeSealedError
}

object Routed extends State {

  override def canArchive: CanResult = successResult

  override def genericError: CanResult = envelopeSealedError
}

object Archived extends State {

  override def genericError: CanResult = envelopeAlreadyArchivedError
}

trait File {
  def fileRefId: FileRefId
  def fileId: FileId
  def name: String

  def isSame(otherFileRefId: FileRefId) =
    fileRefId == otherFileRefId

  def hasError: Boolean = false

  def isAvailable: Boolean = false
}

case class QuarantinedFile(fileRefId: FileRefId, fileId: FileId, name: String) extends File

case class CleanedFile(fileRefId: FileRefId, fileId: FileId, name: String) extends File

case class InfectedFile(fileRefId: FileRefId, fileId: FileId, name: String) extends File {
  override def hasError: Boolean = true
}

case class StoredFile(fileRefId: FileRefId, fileId: FileId, name: String) extends File {
  override def isAvailable: Boolean = true
}


