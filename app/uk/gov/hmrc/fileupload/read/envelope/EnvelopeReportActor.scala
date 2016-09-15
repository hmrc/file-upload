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

package uk.gov.hmrc.fileupload.read.envelope

import akka.actor.{ActorLogging, Props}
import org.joda.time.DateTime
import uk.gov.hmrc.fileupload.read.infrastructure.ReportActor
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.Future

class EnvelopeReportActor(override val id: EnvelopeId,
                          override val get: (EnvelopeId) => Future[Option[Envelope]],
                          override val save: (Envelope) => Future[Boolean],
                          override val delete: (EnvelopeId) => Future[Boolean],
                          override val defaultState: (EnvelopeId) => Envelope) extends ReportActor with ActorLogging {

  override def apply = {

    case (s: Envelope, e: EnvelopeCreated) => withUpdatedVersion {
      s.copy(callbackUrl = e.callbackUrl)
    }

    case (s: Envelope, e: FileQuarantined) => withUpdatedVersion {
      val file = File(fileId = e.fileId, fileRefId = e.fileRefId, status = FileStatusQuarantined, name = Some(e.name),
        contentType = Some(e.contentType), metadata = Some(e.metadata), uploadDate = Some(new DateTime(e.created)) )
      s.copy(files = s.files.orElse(Some(List.empty[File])).map(replaceOrAddFile(_, file)))
    }

    case (s: Envelope, e: NoVirusDetected) => withUpdatedVersion {
      s.copy(files = fileStatusLens(s, e.fileId, FileStatusCleaned))
    }

    case (s: Envelope, e: VirusDetected) => withUpdatedVersion {
      s.copy(files = fileStatusLens(s, e.fileId, FileStatusError))
    }

    case (s: Envelope, e: EnvelopeSealed) => withUpdatedVersion {
      s.copy(destination = Some(e.destination))
    }

    case (s: Envelope, e: EnvelopeRouted) => withUpdatedVersion {
      s.copy(status = EnvelopeStatusClosed)
    }

    case (s: Envelope, e: EnvelopeArchived) => withUpdatedVersion {
      s.copy(status = EnvelopeStatusDeleted)
    }

    case (s: Envelope, e: FileDeleted) => withUpdatedVersion {
      s.copy(files = s.files.orElse(Some(List.empty[File])).map(filterOutFile(_, e.fileId)))
    }

    case (s: Envelope, e: FileStored) => withUpdatedVersion {
      s.copy(files = fileStatusLens(s, e.fileId, FileStatusAvailable))
    }

    case (s: Envelope, e: EnvelopeDeleted) => None
  }

  // todo: version handling will be moved to parent trait
  def withUpdatedVersion(e: Envelope) = Some(e.copy(version = eventVersion))

  def replaceOrAddFile(allFiles: Seq[File], newFile: File): Seq[File] = {
    val filteredFiles = filterOutFile(allFiles, newFile.fileId)
    filteredFiles :+ newFile
  }

  def filterOutFile(allFiles: Seq[File], fileToBeRemoved: FileId): Seq[File] = {
    allFiles.filterNot(f => f.fileId == fileToBeRemoved)
  }

  def fileStatusLens(envelope: Envelope, fileId: FileId, targetStatus: FileStatus) = {
    envelope.files.map { files =>
      files.map { f =>
        if (f.fileId == fileId) {
          f.copy(status = targetStatus)
        } else {
          f
        }
      }
    }
  }

}

object EnvelopeReportActor {

  def props(get: (EnvelopeId) => Future[Option[Envelope]], save: (Envelope) => Future[Boolean],
            delete: EnvelopeId => Future[Boolean], defaultState: (EnvelopeId) => Envelope)
           (id: EnvelopeId) =
    Props(new EnvelopeReportActor(id, get, save, delete, defaultState))
}
