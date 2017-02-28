/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload.write.infrastructure.{EventData, Handler}
import uk.gov.hmrc.fileupload.{FileId, FileRefId}

object Envelope extends Handler[EnvelopeCommand, Envelope] {

  val defaultMaxNumFilesCapacity = 100
  val defaultMaxSize = 25 //MB

  type CanResult = Xor[EnvelopeCommandNotAccepted, Unit.type]

  override def handle = {
    case (command: CreateEnvelope, envelope: Envelope) =>
      envelope.canCreateWithFilesCapacityAndSize(command.maxFilesCapacity, command.maxSize).map(_ =>
        EnvelopeCreated(command.id, command.callbackUrl, command.expiryDate, command.metadata, command.maxFilesCapacity, command.maxSize)
      )

    case (command: QuarantineFile, envelope: Envelope) =>
      envelope.canQuarantine(command.fileId, command.fileRefId, command.name, command.fileLength, envelope).map(_ =>
        FileQuarantined(
          id = command.id, fileId = command.fileId, fileRefId = command.fileRefId,
          created = command.created, name = command.name, fileLength = command.fileLength, contentType = command.contentType, metadata = command.metadata)
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
      envelope.canStoreFile(command.fileId, command.fileRefId, command.fileLength, envelope).map { _ =>
        val fileStored = FileStored(command.id, command.fileId, command.fileRefId, command.fileLength)

        if (withEvent(envelope, fileStored).canRoute.isRight) {
          fileStored And EnvelopeRouted(command.id)
        } else {
          fileStored
        }
      }

    case (command: DeleteFile, envelope: Envelope) =>
      envelope.canDeleteFile(command.fileId).map (_ => FileDeleted(command.id, command.fileId))

    case (command: DeleteEnvelope, envelope: Envelope) =>
      envelope.canDelete.map(_ => EnvelopeDeleted(command.id))

    case (command: SealEnvelope, envelope: Envelope) =>
      envelope.canSeal(command.destination).map(_ => {
        val envelopeSealed = EnvelopeSealed(command.id, command.routingRequestId, command.destination, command.application)

        if (withEvent(envelope, envelopeSealed).canRoute.isRight) {
          envelopeSealed And EnvelopeRouted(command.id)
        } else {
          envelopeSealed
        }
      })

    case (command: UnsealEnvelope, envelope: Envelope) =>
      envelope.canUnseal.map(_ => {
        EnvelopeUnsealed(command.id)
      })

    case (command: ArchiveEnvelope, envelope: Envelope) =>
      envelope.canArchive.map(_ => EnvelopeArchived(command.id))
  }

  override def on = {
      case (envelope: Envelope, e: EnvelopeCreated) =>
        envelope.copy(state = Open, fileCapacity = e.maxNumFiles, maxSize = e.maxSize)

      case (envelope: Envelope, e: FileQuarantined) =>
        envelope.copy(files = envelope.files + (e.fileId -> QuarantinedFile(e.fileRefId, e.fileId, e.name, e.fileLength)))

      case (envelope: Envelope, e: NoVirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> CleanedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, envelope.files(e.fileId).fileLength)))

      case (envelope: Envelope, e: VirusDetected) =>
        envelope.copy(files = envelope.files + (e.fileId -> InfectedFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, envelope.files(e.fileId).fileLength)))

      case (envelope: Envelope, e: FileDeleted) =>
        envelope.copy(files = envelope.files - e.fileId)

      case (envelope: Envelope, e: FileStored) =>
        envelope.copy(files = envelope.files + (e.fileId -> StoredFile(e.fileRefId, e.fileId, envelope.files(e.fileId).name, envelope.files(e.fileId).fileLength)))

      case (envelope: Envelope, e: EnvelopeDeleted) =>
        envelope.copy(state = Deleted)

      case (envelope: Envelope, e: EnvelopeSealed) =>
        envelope.copy(state = Sealed)

      case (envelope: Envelope, e: EnvelopeUnsealed) =>
        envelope.copy(state = Open)

      case (envelope: Envelope, e: EnvelopeRouted) =>
        envelope.copy(state = Routed)

      case (envelope: Envelope, e: EnvelopeArchived) =>
        envelope.copy(state = Archived)

      case (envelope: Envelope, e: EnvelopeIsFull) =>
        envelope.copy(state = Full)
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
    def And(another: EventData) = items :+ another
  }
}

case class Envelope(files: Map[FileId, File] = Map.empty, state: State = NotCreated, fileCapacity: Int = 0, maxSize: String = "0MB") {

  def canCreateWithFilesCapacityAndSize(maxFiles: Int, maxSize: String): CanResult = state.canCreateWithFilesCapacityAndSize(maxFiles, maxSize)

  def canDeleteFile(fileId: FileId): CanResult = state.canDeleteFile(fileId, files)

  def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String, fileLength: Long, envelope: Envelope): CanResult = state.canQuarantine(fileId, fileRefId, name, fileLength, files, envelope)

  def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId): CanResult = state.canMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  def canStoreFile(fileId: FileId, fileRefId: FileRefId, fileLength: Long, envelope: Envelope): CanResult = state.canStoreFile(fileId, fileRefId, fileLength, files, envelope)

  def canSeal(destination: String): CanResult = state.canSeal(files.values.toSeq, destination)

  def canUnseal: CanResult = state.canUnseal

  def canDelete: CanResult = state.canDelete

  def canRoute: CanResult = state.canRoute(files.values.toSeq)

  def canArchive: CanResult = state.canArchive

  def isFull: CanResult = state.isFull
}

object State {
  val successResult = Xor.right(Unit)
  val envelopeNotFoundError = Xor.left(EnvelopeNotFoundError)
  val envelopeAlreadyCreatedError = Xor.left(EnvelopeAlreadyCreatedError)
  val envelopeMaxNumFilesExceededError = Xor.left(EnvelopeMaxNumFilesExceededError)
  val envelopeMaxSizeExceededError = Xor.left(EnvelopeMaxSizeExceededError)
  val fileNotFoundError = Xor.left(FileNotFoundError)
  val envelopeSealedError = Xor.left(EnvelopeSealedError)
  val envelopeAlreadyArchivedError = Xor.left(EnvelopeArchivedError)
  val envelopeAlreadyRoutedError = Xor.left(EnvelopeAlreadyRoutedError)
  val fileAlreadyProcessedError = Xor.left(FileAlreadyProcessed)
}

sealed trait State {
  import State._

  def canCreateWithFilesCapacityAndSize(maxFiles: Int, maxSize: String): CanResult = envelopeAlreadyCreatedError

  def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult = genericError

  def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String,  fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult = genericError

  def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult = genericError

  def canStoreFile(fileId: FileId, fileRefId: FileRefId, fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult = genericError

  def canSeal(files: Seq[File], destination: String): CanResult = genericError

  def canUnseal(): CanResult = genericError

  def canDelete: CanResult = genericError

  def canRoute(files: Seq[File]): CanResult = genericError

  def canArchive: CanResult = genericError

  def isFull: CanResult = envelopeMaxNumFilesExceededError

  def genericError: CanResult = envelopeNotFoundError

  def checkCanMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    files.get(fileId).filter(_.isSame(fileRefId)).map(f =>
      if (!f.isScanned && !f.isAvailable) {
        successResult
      } else {
        Xor.left(FileAlreadyProcessed)
      }).getOrElse(fileNotFoundError)

  def checkCanFileQuarantined(fileId: FileId, fileRefId: FileRefId, fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult = {

    def sizeToByte(size: String): Long = {
      val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r
      size.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => (num.toInt * 1024).toLong
            case "MB" => (num.toInt * 1024 * 1024).toLong
            case _ => 0
          }
        case _ => 0
      }
    }

    val envelopeMaxSize: Long = sizeToByte(envelope.maxSize)
    val currentSize: Long = envelope.files.map(file => file._2.fileLength).sum
    val furtherSize: Long = currentSize + fileLength

    if (envelope.files.size < envelope.fileCapacity && furtherSize <= envelopeMaxSize) {
      files.get(fileId).filter(_.isSame(fileRefId)).map(_ => Xor.left(FileAlreadyProcessed)).getOrElse(successResult)
    } else if (furtherSize > envelopeMaxSize) envelopeMaxSizeExceededError
    else isFull

  }

  def checkCanStoreFile(fileId: FileId, fileRefId: FileRefId, fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult = {
    files.get(fileId).filter(_.isSame(fileRefId)).map(f => {
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
    }).getOrElse(fileNotFoundError)
  }

}

object NotCreated extends State {
  import State._

  override def canCreateWithFilesCapacityAndSize(maxFiles: Int, maxSize: String): CanResult =
    (maxFiles, maxSize)match {
      case (num, size) => if (num > Envelope.defaultMaxNumFilesCapacity) envelopeMaxNumFilesExceededError
                          else if (!isValidEnvelopeSize(size)) envelopeMaxSizeExceededError
                          else successResult
      case _ => successResult
    }

  def isValidEnvelopeSize(size: String): Boolean = {
    val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r
    size.toUpperCase match {
      case sizeRegex(num, unit) =>
        unit match {
          case "KB" => true
          case "MB" => if (num.toInt <= Envelope.defaultMaxSize) true
                       else false
        }
      case _ => false
    }
  }
}

object Full extends State {
  import State._
    envelopeMaxNumFilesExceededError
}

object Open extends State {
  import State._

  override def canDeleteFile(fileId: FileId, files: Map[FileId, File]): CanResult =
    files.get(fileId).map(f => successResult).getOrElse(fileNotFoundError)

  // Could be useful in the future (should we check for name duplicates):
  // files.find(f => f.fileId != fileId && f.name == name).map(f => Xor.Left(FileNameDuplicateError(f.fileId))).getOrElse(successResult)
  override def canQuarantine(fileId: FileId, fileRefId: FileRefId, name: String,  fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult =
    checkCanFileQuarantined(fileId, fileRefId, fileLength, files, envelope)

  override def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  override def canStoreFile(fileId: FileId, fileRefId: FileRefId, fileLength:Long, files: Map[FileId, File], envelope: Envelope): CanResult =
    checkCanStoreFile(fileId, fileRefId, fileLength, files, envelope)

  override def canDelete: CanResult = successResult

  override def canSeal(files: Seq[File], destination: String): CanResult = {
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
  import State._

  override def canMarkFileAsCleanOrInfected(fileId: FileId, fileRefId: FileRefId, files: Map[FileId, File]): CanResult =
    checkCanMarkFileAsCleanOrInfected(fileId, fileRefId, files)

  override def canStoreFile(fileId: FileId, fileRefId: FileRefId, fileLength: Long, files: Map[FileId, File], envelope: Envelope): CanResult =
    checkCanStoreFile(fileId, fileRefId, fileLength, files, envelope)

  override def canUnseal(): CanResult = successResult

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
  import State._

  override def canArchive: CanResult = successResult

  override def genericError: CanResult = envelopeAlreadyRoutedError
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

  def isSame(otherFileRefId: FileRefId) =
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
