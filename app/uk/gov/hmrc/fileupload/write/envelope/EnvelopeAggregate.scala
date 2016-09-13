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

import uk.gov.hmrc.fileupload.write.infrastructure.AggregateRoot._
import uk.gov.hmrc.fileupload.write.infrastructure.{AggregateRoot, EventStore}
import uk.gov.hmrc.fileupload.{FileId, FileRefId}

class EnvelopeAggregate(override val defaultState: () => Envelope = () => Envelope())
                       (implicit val eventStore: EventStore, implicit val publish: AnyRef => Unit) extends AggregateRoot[EnvelopeCommand, Envelope] {

  override def handle = {
    case (command: CreateEnvelope, envelope: Envelope) =>
      EnvelopeCreated(command.id, command.callbackUrl)

    case (command: QuarantineFile, envelope: Envelope) =>
      if (envelope.canQuarantine) {
        FileQuarantined(
          id = command.id, fileId = command.fileId, fileRefId = command.fileRefId,
          name = command.name, contentType = command.contentType, metadata = command.metadata)
      } else {
        "not the right status"
      }

    case (command: MarkFileAsClean, envelope: Envelope) =>
      if (envelope.sameFileReferenceId(command.fileId, command.fileRefId)) {
        NoVirusDetected(command.id, command.fileId, command.fileRefId)
      } else {
        "not the right file"
      }

    case (command: MarkFileAsInfected, envelope: Envelope) =>
      if (envelope.sameFileReferenceId(command.fileId, command.fileRefId)) {
        VirusDetected(command.id, command.fileId, command.fileRefId)
      } else {
        "not the right file"
      }

    case (command: StoreFile, envelope: Envelope) =>
      if (envelope.sameFileReferenceId(command.fileId, command.fileReferenceId)) {
        val fileStored = FileStored(command.id, command.fileId, command.fileReferenceId, command.length)

        if (envelope.canRoute) {
          List(fileStored, EnvelopeRouted(command.id))
        } else {
          fileStored
        }
      } else {
        "not the right file"
      }

    case (command: DeleteFile, envelope: Envelope) =>
      if (envelope.canDeleteFile) {
        val fileDeleted = FileDeleted(command.id, command.fileId)

        if (envelope.canRoute) {
          List(fileDeleted, EnvelopeRouted(command.id))
        } else {
          fileDeleted
        }
      } else {
        "not the right status"
      }

    case (command: DeleteEnvelope, envelope: Envelope) =>
      if (envelope.canDelete) {
        EnvelopeDeleted(command.id)
      } else {
        "not the right status"
      }

    case (command: SealEnvelope, envelope: Envelope) =>
      if (envelope.canSeal) {
        EnvelopeSealed(command.id, command.destination, command.packageType)
      } else {
        "not the right status"
      }

    case (command: ArchiveEnvelope, envelope: Envelope) =>
      if (envelope.canArchive) {
        EnvelopeArchived(command.id)
      } else {
        "not the right status"
      }
  }

  override def apply = {
      case (envelope: Envelope, e: EnvelopeCreated) =>
        envelope.copy()

      case (envelope: Envelope, e: FileQuarantined) =>
        envelope.copy(files = envelope.files + (e.fileId -> QuarantinedFile(e.fileRefId, e.fileId)))

      case (envelope: Envelope, e: NoVirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> CleanedFile(e.fileRefId, e.fileId)))

      case (envelope: Envelope, e: VirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> InfectedFile(e.fileRefId, e.fileId)))

      case (envelope: Envelope, e: FileDeleted) =>
        envelope.copy(files = envelope.files - e.fileId)

      case (envelope: Envelope, e: FileStored) =>
        envelope.copy(files = envelope.files + (e.fileId -> StoredFile(e.fileRefId, e.fileId)))

      case (envelope: Envelope, e: EnvelopeDeleted) =>
        envelope.copy(state = Deleted)

      case (envelope: Envelope, e: EnvelopeSealed) =>
        envelope.copy(state = Sealed)

      case (envelope: Envelope, e: EnvelopeRouted) =>
        envelope.copy(state = Routed)

      case (envelope: Envelope, e: EnvelopeArchived) =>
        envelope.copy(state = Archived)
  }
}

case class Envelope(files: Map[FileId, File] = Map.empty, state: State = Open) {

  def sameFileReferenceId(fileId: FileId, fileReferenceId: FileRefId): Boolean =
    files.get(fileId).exists(_.isSame(fileReferenceId))

  def canDeleteFile: Boolean = state.canDeleteFile

  def canQuarantine: Boolean = state.canQuarantine

  def canSeal: Boolean = state.canSeal(files.values.toSeq)

  def canDelete: Boolean = state.canDelete

  def canRoute: Boolean = state.canRoute(files.values.toSeq)

  def canArchive: Boolean = state.canArchive
}

sealed trait State {

  def canDeleteFile: Boolean = false

  def canQuarantine: Boolean = false

  def canSeal(files: Seq[File]): Boolean = false

  def canDelete: Boolean = false

  def canRoute(files: Seq[File]): Boolean = false

  def canArchive: Boolean = false
}

object Open extends State {

  override def canDeleteFile: Boolean = true

  override def canQuarantine: Boolean = true

  override def canDelete: Boolean = true

  override def canSeal(files: Seq[File]): Boolean =
    !files.exists(!_.hasError)
}

object Deleted extends State

object Sealed extends State {

  override def canRoute(files: Seq[File]) =
    !files.exists(!_.isAvailable)
}

object Routed extends State {

  override def canArchive: Boolean = true
}

object Archived extends State

trait File {
  def fileRefId: FileRefId
  def fileId: FileId

  def isSame(otherFileRefId: FileRefId) =
    fileRefId == otherFileRefId

  def hasError: Boolean = false

  def isAvailable: Boolean = false
}

case class QuarantinedFile(fileRefId: FileRefId, fileId: FileId) extends File

case class CleanedFile(fileRefId: FileRefId, fileId: FileId) extends File

case class InfectedFile(fileRefId: FileRefId, fileId: FileId) extends File {
  override def hasError: Boolean = true
}

case class StoredFile(fileRefId: FileRefId, fileId: FileId) extends File {
  override def isAvailable: Boolean = true
}


