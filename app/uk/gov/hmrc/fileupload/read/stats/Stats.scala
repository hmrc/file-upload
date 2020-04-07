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

package uk.gov.hmrc.fileupload.read.stats

import cats.data.Xor
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object Stats {

  type GetInProgressFileResult = GetInProgressFileError Xor List[InProgressFile]
  case class FileFound(name: Option[String] = None, length: Long, data: Enumerator[Array[Byte]])
  sealed trait GetInProgressFileError
  object GetInProgressFileGenericError extends GetInProgressFileError

  def save(insert: (InProgressFile) => Future[WriteResult])(fileQuarantined: FileQuarantined)
          (implicit ec: ExecutionContext): Unit = {
    Future {
      Logger.info(s"Currently in progress for file: ${fileQuarantined.fileId}, at: ${fileQuarantined.fileRefId}, " +
                  s"started on: ${fileQuarantined.created}, For envelope ${fileQuarantined.id}")
      insert(InProgressFile(_id = fileQuarantined.fileRefId, envelopeId = fileQuarantined.id, fileId = fileQuarantined.fileId,
                            startedAt = fileQuarantined.created))
    }.onFailure {
      case e => Logger.warn(s"It was not possible to store an in progress file for ${fileQuarantined.id}" +
                            s" - ${fileQuarantined.fileId} - ${fileQuarantined.fileRefId}", e)
    }
  }

  def deleteVirusDetected(deleteInProgressFile: (EnvelopeId, FileId) => Future[Boolean])(virusDetected: VirusDetected)
                         (implicit ec: ExecutionContext): Unit = {
    Future {
      deleteInProgressFile(virusDetected.id, virusDetected.fileId)
    }.onFailure {
      case e => Logger.warn(s"It was not possible to delete the virus file for " +
                            s"${virusDetected.id} - ${virusDetected.fileId} - ${virusDetected.fileRefId}", e)
    }
  }

  def deleteFileStored(deleteInProgressFile: (EnvelopeId, FileId) => Future[Boolean])(fileStored: FileStored)
                      (implicit ec: ExecutionContext): Unit = {
    Future {
      deleteInProgressFile(fileStored.id, fileStored.fileId)
    }.onFailure {
      case e => Logger.warn(s"It was not possible to delete the file for ${fileStored.id} - ${fileStored.fileId} - ${fileStored.fileRefId}", e)
    }
  }

  def deleteEnvelopeFiles(deleteEnvelope: (EnvelopeId) => Future[Boolean])(envelopeDeleted: EnvelopeDeleted)
                         (implicit ec: ExecutionContext): Unit = {
    Future {
      deleteEnvelope(envelopeDeleted.id)
    }.onFailure {
      case e => Logger.warn(s"It was not possible to delete the envelope for ${envelopeDeleted.id}", e)
    }
  }

  def all(findAllInProgressFile: () => Future[List[InProgressFile]])()
         (implicit ec: ExecutionContext): Future[GetInProgressFileResult] = {
    findAllInProgressFile().map(Xor.right).recover {
      case e =>
        Logger.warn("It was not possible to retrieve in progress files", e)
        Xor.left(GetInProgressFileGenericError)
    }
  }
}
