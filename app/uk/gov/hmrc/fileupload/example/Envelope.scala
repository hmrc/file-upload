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

package uk.gov.hmrc.fileupload.example

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}
import uk.gov.hmrc.fileupload.domain.{AggregateRoot, Event}

class Envelope(override val id: EnvelopeId) extends AggregateRoot {

  var files = Map.empty[FileId, File]

  def create(envelopeId: EnvelopeId) =
    applyChange(EnvelopeCreated(envelopeId))

  def quarantineFile(fileId: FileId, fileReferenceId: FileReferenceId) =
    applyChange(FileQuarantined(id, fileId, fileReferenceId))

  def cleanFile(fileId: FileId, fileReferenceId: FileReferenceId) =
    files.get(fileId).foreach { file =>
      if (file.isSame(fileReferenceId)) {
        applyChange(FileCleaned(id, fileId, fileReferenceId))
      }
    }

  def apply(event: Event) = {
    event.eventData match {
      case e: EnvelopeCreated =>

      case e: FileQuarantined =>
        files = files + (e.fileId -> QuarantinedFile(e.fileReferenceId, e.fileId))
      case e: FileCleaned =>
        files = files + (e.fileId -> CleanedFile(e.fileReferenceId, e.fileId))
      case _ => println("not implemented")
    }
  }
}

trait File {
  def fileReferenceId: FileReferenceId
  def fileId: FileId

  def isSame(otherFileReferenceId: FileReferenceId) =
    fileReferenceId == otherFileReferenceId
}

case class QuarantinedFile(fileReferenceId: FileReferenceId, fileId: FileId) extends File
case class CleanedFile(fileReferenceId: FileReferenceId, fileId: FileId) extends File
case class InfectedFile(fileReferenceId: FileReferenceId, fileId: FileId) extends File
case class AvailableFile(fileReferenceId: FileReferenceId, fileId: FileId) extends File
