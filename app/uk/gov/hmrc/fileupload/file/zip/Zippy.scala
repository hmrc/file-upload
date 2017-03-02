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

package uk.gov.hmrc.fileupload.file.zip

import java.io.{BufferedOutputStream, ByteArrayOutputStream}
import java.util.UUID
import java.util.zip.ZipOutputStream

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.file.zip.Utils.Bytes
import uk.gov.hmrc.fileupload.file.zip.ZipStream.{ZipFileInfo, ZipStreamEnumerator}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatusClosed}
import uk.gov.hmrc.fileupload.read.file.Service.{FileFound, GetFileResult}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object Zippy {

  type ZipResult = Xor[ZipEnvelopeError, Enumerator[Bytes]]
  sealed trait ZipEnvelopeError
  case object ZipEnvelopeNotFoundError extends ZipEnvelopeError
  case object EnvelopeNotRoutedYet extends ZipEnvelopeError
  case class ZipProcessingError(message: String) extends ZipEnvelopeError


  def zipEnvelope(getEnvelope: (EnvelopeId) => Future[FindResult], retrieveFile: (Envelope, FileId) => Future[GetFileResult])
                 (envelopeId: EnvelopeId)
                 (implicit ec: ExecutionContext): Future[ZipResult] = {

    getEnvelope(envelopeId) map {
      case Xor.Right(envelopeWithFiles @ Envelope(_, _, EnvelopeStatusClosed, _, _, _, Some(files), _, _, _, _, _)) =>
        val zipFiles = files.collect {
          case f =>
            val fileName = f.name.getOrElse(UUID.randomUUID().toString)
            ZipFileInfo(fileName, isDir = false, new java.util.Date(), Some(() => retrieveFile(envelopeWithFiles, f.fileId).map {
              case Xor.Right(FileFound(name, length, data)) => data
              case Xor.Left(error) => throw new Exception(s"File $envelopeId ${f.fileId} not found in repo" )
            }))
        }
        Xor.right( ZipStreamEnumerator(zipFiles))

      case Xor.Right(envelopeWithoutFiles @ Envelope(_, _, EnvelopeStatusClosed, _, _, _, None, _, _, _, _, _)) =>

        Xor.Right(emptyZip())

      case Xor.Right(envelopeWithWrongStatus: Envelope) => Xor.left(EnvelopeNotRoutedYet)

      case Xor.Left(FindEnvelopeNotFoundError) => Xor.left(ZipEnvelopeNotFoundError)

      case Xor.Left(FindServiceError(message)) => Xor.left(ZipProcessingError(message))
    }
  }

  def emptyZip() = {
    val baos = new ByteArrayOutputStream()
    val out =  new ZipOutputStream(new BufferedOutputStream(baos))
    out.close()
    Enumerator(baos.toByteArray)
  }

}
