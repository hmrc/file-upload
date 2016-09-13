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

package uk.gov.hmrc.fileupload.read.file

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.{FileId, FileRefId}
import uk.gov.hmrc.fileupload.read.envelope.Envelope

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object Service {

  type GetFileResult = GetFileError Xor FileFound
  case class FileFound(name: Option[String] = None, length: Long, data: Enumerator[Array[Byte]])
  sealed trait GetFileError
  case object GetFileNotFoundError extends GetFileError

  def retrieveFile(getFileFromRepo: FileRefId => Future[Option[FileData]])
                  (envelope: Envelope, fileId: FileId)
                  (implicit ex: ExecutionContext): Future[GetFileResult] =
    (for {
      file <- envelope.getFileById(fileId)
    } yield {
      getFileFromRepo(file.fileRefId).map { maybeData =>
        val fileWithClientProvidedName = maybeData.map { d => FileFound(file.name, d.length, d.data) }
        Xor.fromOption(fileWithClientProvidedName, ifNone = GetFileNotFoundError)
      }
    }).getOrElse(Future.successful(Xor.left(GetFileNotFoundError)))

}
