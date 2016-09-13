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
import uk.gov.hmrc.fileupload.domain._
import uk.gov.hmrc.fileupload.write.envelope.Envelope.{CleanedFile, QuarantinedFile, State}
import uk.gov.hmrc.fileupload.{FileId, FileRefId}

class Envelope(override val defaultState: () => State = () => State())
              (implicit val eventStore: EventStore, implicit val publish: AnyRef => Unit) extends AggregateRoot[EnvelopeCommand, State] {

  override def handle = {
    case (command: CreateEnvelope, state: State) =>
      Xor.Right(List(EnvelopeCreated(command.id)))

    case (command: QuarantineFile, state: State) =>
      Xor.Right(List(FileQuarantined(
        id = command.id, fileId = command.fileId, fileRefId = command.fileRefId,
        name = command.name, contentType = command.contentType, metadata = command.metadata)))

    case (command: MarkFileAsClean, state: State) =>
      if (state.sameFileReferenceId(command.fileId, command.fileRefId)) {
        Xor.Right(List(NoVirusDetected(command.id, command.fileId, command.fileRefId)))
      } else {
        Xor.Left("not the right file")
      }
  }

  override def apply = {
      case (state: State, e: EnvelopeCreated) =>
        state.copy()

      case (state: State, e: FileQuarantined) =>
        state.copy(files = state.files + (e.fileId -> QuarantinedFile(e.fileRefId, e.fileId)))

      case (state: State, e: NoVirusDetected) =>
        state.copy(files = state.files + (e.fileId -> CleanedFile(e.fileRefId, e.fileId)))
  }
}

object Envelope {

  case class State(files: Map[FileId, File] = Map.empty) {

    def sameFileReferenceId(fileId: FileId, fileReferenceId: FileRefId): Boolean =
      files.get(fileId).exists(_.isSame(fileReferenceId))
  }

  trait File {
    def fileReferenceId: FileRefId
    def fileId: FileId

    def isSame(otherFileReferenceId: FileRefId) =
      fileReferenceId == otherFileReferenceId
  }

  case class QuarantinedFile(fileReferenceId: FileRefId, fileId: FileId) extends File
  case class CleanedFile(fileReferenceId: FileRefId, fileId: FileId) extends File
  case class InfectedFile(fileReferenceId: FileRefId, fileId: FileId) extends File
  case class AvailableFile(fileReferenceId: FileRefId, fileId: FileId) extends File

}


