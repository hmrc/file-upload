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

package uk.gov.hmrc.fileupload.stats

import cats.data.Xor
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.infrastructure.DefaultMongoConnection
import uk.gov.hmrc.fileupload.write.envelope.{FileQuarantined, FileStored, VirusDetected}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Stats {

  lazy val db = DefaultMongoConnection.db
  lazy val repo = Repository.apply(db)

  type GetInProgressFileResult = GetInProgressFileError Xor List[InProgressFile]
  case class FileFound(name: Option[String] = None, length: Long, data: Enumerator[Array[Byte]])
  sealed trait GetInProgressFileError
  object GetInProgressFileGenericError extends GetInProgressFileError

  def save(fileQuarantined: FileQuarantined) = {
    Future {
      repo.insert(InProgressFile(_id = fileQuarantined.fileRefId, envelopeId = fileQuarantined.id, fileId = fileQuarantined.fileId, startedAt = fileQuarantined.created))
    }.onFailure {
      case e => Logger.error("It was not possible to store an in progress file", e)
    }
  }

  def delete(virusDetected: VirusDetected): Unit = {
      delete(virusDetected.id, virusDetected.fileId, virusDetected.fileRefId)
  }

  def delete(fileStored: FileStored): Unit = {
    delete(fileStored.id, fileStored.fileId, fileStored.fileRefId)
  }

  private def delete(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): Unit = {
    Future {
      repo.delete(envelopeId, fileId, fileRefId)
    }.onFailure {
      case e => Logger.error("It was not possible to store an in progress file", e)
    }
  }

  def all(): Future[GetInProgressFileResult] = {
    repo.findAll().map {
      case inProgressFiles @ List(_) => Xor.right(inProgressFiles)
      case Nil => Xor.right(List())
    }.recover {
      case e =>
        Logger.error("It was not possible to retrieve in progress files", e)
        Xor.left(GetInProgressFileGenericError)
    }
  }
}
