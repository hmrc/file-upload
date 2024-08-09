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

package uk.gov.hmrc.fileupload.read.envelope

import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.fileupload.read.envelope.Repository.{DeleteResult, UpdateResult}
import uk.gov.hmrc.fileupload.read.infrastructure.ReportHandler
import uk.gov.hmrc.fileupload.write.envelope.{Envelope => _, File => _, _}
import uk.gov.hmrc.fileupload.write.infrastructure.{EventData, StreamId, Version}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

class EnvelopeReportHandler(
  override val toId         : StreamId => EnvelopeId,
  override val update       : (Envelope, Boolean) => Future[UpdateResult],
  override val delete       : EnvelopeId => Future[DeleteResult],
  override val defaultState : EnvelopeId => Envelope,
  override val updateVersion: (Version, Envelope) => Envelope = (v, e) => e.copy(version = v)
)(implicit
  override val ec: ExecutionContext
) extends ReportHandler[Envelope, EnvelopeId] {

  override def apply: PartialFunction[(Envelope, EventData), Option[Envelope]] = {

    case (s: Envelope, e: EnvelopeCreated) =>
      Some(s.copy(
        callbackUrl = e.callbackUrl,
        expiryDate  = e.expiryDate,
        metadata    = e.metadata,
        constraints = e.constraints
      ))

    case (s: Envelope, e: FileQuarantined) =>
      val file = File(
        fileId      = e.fileId,
        fileRefId   = e.fileRefId,
        status      = FileStatusQuarantined,
        name        = Some(e.name),
        contentType = Some(e.contentType),
        metadata    = Some(e.metadata),
        uploadDate  = Some(new DateTime(e.created, DateTimeZone.UTC)),
        length      = e.length
        )
      Some(s.copy(
        files = s.files.orElse(Some(List.empty[File])).map(replaceOrAddFile(_, file))
      ))

    case (s: Envelope, e: NoVirusDetected) =>
      Some(s.copy(files = fileStatusLens(s, e.fileId, FileStatusCleaned)))

    case (s: Envelope, e: VirusDetected) =>
      Some(s.copy(
        files = fileStatusLens(s, e.fileId, FileStatusInfected)
      ))

    case (s: Envelope, e: EnvelopeSealed) =>
      Some(s.copy(
        status      = EnvelopeStatusSealed,
        destination = Some(e.destination),
        application = Some(e.application)
      ))

    case (s: Envelope, e: EnvelopeUnsealed) =>
      Some(s.copy(
        status      = EnvelopeStatusOpen,
        destination = None,
        application = None
      ))

    case (s: Envelope, e: EnvelopeRouteRequested) =>
      Some(s.copy(
        status     = EnvelopeStatusRouteRequested,
        lastPushed = e.lastPushed
      ))

    case (s: Envelope, e: EnvelopeArchived) =>
      Some(s.copy(
        status = EnvelopeStatusDeleted,
        reason = e.reason
      ))

    case (s: Envelope, e: EnvelopeRouted) =>
      Some(s.copy(
        status   = EnvelopeStatusClosed,
        isPushed = Some(e.isPushed),
        reason   = e.reason
      ))

    case (s: Envelope, e: FileDeleted) =>
      Some(s.copy(
        files = s.files.orElse(Some(List.empty[File])).map(filterOutFile(_, e.fileId))
      ))

    case (s: Envelope, e: FileStored) =>
      val withUpdatedStatus = s.copy(files = fileStatusLens(s, e.fileId, FileStatusAvailable))
      Some(withUpdatedStatus.copy(
        files = fileLengthLens(withUpdatedStatus, e.fileId, e.length)
      ))

    case (s: Envelope, e: EnvelopeDeleted) => None
  }

  def replaceOrAddFile(allFiles: Seq[File], newFile: File): Seq[File] =
    filterOutFile(allFiles, newFile.fileId) :+ newFile

  def filterOutFile(allFiles: Seq[File], fileToBeRemoved: FileId): Seq[File] =
    allFiles.filterNot(f => f.fileId == fileToBeRemoved)

  def fileStatusLens(envelope: Envelope, fileId: FileId, targetStatus: FileStatus): Option[Seq[File]] =
    envelope.files.map { files =>
      files.map { f =>
        if (f.fileId == fileId)
          f.copy(status = targetStatus)
        else
          f
      }
    }

  def fileLengthLens(envelope: Envelope, fileId: FileId, length: Long): Option[Seq[File]] =
    envelope.files.map { files =>
      files.map { f =>
        if (f.fileId == fileId)
          f.copy(length = Some(length))
        else
          f
      }
    }
}
