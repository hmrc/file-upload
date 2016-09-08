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

import uk.gov.hmrc.fileupload.domain.{Command, StreamId}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}

//commands

sealed trait EnvelopeCommand extends Command {
  def id: EnvelopeId

  def streamId: StreamId = StreamId(id.toString)
}

case class CreateEnvelope(id: EnvelopeId) extends EnvelopeCommand

case class QurantineFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class CleanFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class InfectFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class StoreFile(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId) extends EnvelopeCommand

case class SealEnvelope(id: EnvelopeId) extends EnvelopeCommand

//events

case class EnvelopeCreated(id: EnvelopeId)

case class FileQuarantined(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileCleaned(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileInfected(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class FileStored(id: EnvelopeId, fileId: FileId, fileReferenceId: FileReferenceId)

case class EnvelopeSealed(id: EnvelopeId)
