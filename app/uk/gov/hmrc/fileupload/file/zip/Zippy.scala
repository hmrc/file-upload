/*
 * Copyright 2018 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import cats.data.Xor
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.streams.Streams
import uk.gov.hmrc.fileupload.file.zip.Utils.Bytes
import uk.gov.hmrc.fileupload.file.zip.ZipStream.{ZipFileInfo, ZipStreamEnumerator}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatusClosed}
import uk.gov.hmrc.fileupload.read.file.FileData
import uk.gov.hmrc.fileupload.read.stats.Stats.FileFound
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

object Zippy {

  import MongoS3Compability._

  type ZipResult = Xor[ZipEnvelopeError, Enumerator[Bytes]]
  sealed trait ZipEnvelopeError
  case object ZipEnvelopeNotFoundError extends ZipEnvelopeError
  case object EnvelopeNotRoutedYet extends ZipEnvelopeError
  case class ZipProcessingError(message: String) extends ZipEnvelopeError

  def zipEnvelope(getEnvelope: (EnvelopeId) => Future[FindResult],
                  retrieveS3File: (EnvelopeId, FileId) => Future[Source[ByteString, _]],
                  //Todo: remove else when mongoDB is not in use at all.
                  retrieveMongoFile: (Envelope, FileId) => Future[GetFileResult] =
                  (_,_) => Future.failed(new UnsupportedOperationException))
                 (envelopeId: EnvelopeId)
                 (implicit ec: ExecutionContext, mat: Materializer): Future[ZipResult] = {

    def sourceToEnumerator(source: Source[ByteString, _]) = {
      val byteStringToArrayOfBytes = Flow[ByteString].map(_.toArray)
      Streams.publisherToEnumerator(
        source.via(byteStringToArrayOfBytes).runWith(Sink.asPublisher(fanout = false))
      )
    }

    getEnvelope(envelopeId) map {
      case Xor.Right(envelopeWithFiles @ Envelope(_, _, EnvelopeStatusClosed, _, _, _, _, Some(files), _, _)) =>
        val zipFiles = files.collect {
          case f =>
            val fileName = f.name.getOrElse(UUID.randomUUID().toString)
            val fileInS3 = checkIsTheFileInS3(f.fileRefId)
            Logger.info(s"""zipEnvelope: envelopeId=${envelopeWithFiles._id} fileId=${f.fileId} fileRefId=${f.fileRefId} length=${f.length.getOrElse(-1)} uploadDate=${f.uploadDate.getOrElse("-")}""")
            ZipFileInfo(
              fileName, isDir = false, new java.util.Date(),
              Some(() => {
                if (fileInS3) retrieveS3File(envelopeWithFiles._id, f.fileId).map { sourceToEnumerator }
                //Todo: remove if-else when mongoDB is not in use at all.
                else retrieveMongoFile(envelopeWithFiles, f.fileId).map {
                  case Xor.Right(FileFound(name, length, data)) => data
                  case Xor.Left(GetFileNotFoundError) => throw new Exception(s"File $envelopeId ${f.fileId} not found in repo" )
                }
              })
            )
        }
        Xor.right( ZipStreamEnumerator(zipFiles))

      case Xor.Right(envelopeWithoutFiles @ Envelope(_, _, EnvelopeStatusClosed, _, _, _, _, None, _, _)) =>

        Xor.Right(emptyZip())

      case Xor.Right(envelopeWithWrongStatus: Envelope) => Xor.left(EnvelopeNotRoutedYet)

      case Xor.Left(FindEnvelopeNotFoundError) => Xor.left(ZipEnvelopeNotFoundError)

      case Xor.Left(FindServiceError(message)) => Xor.left(ZipProcessingError(message))
    }
  }

  def emptyZip(): Enumerator[Array[Byte]] = {
    val baos = new ByteArrayOutputStream()
    val out =  new ZipOutputStream(new BufferedOutputStream(baos))
    out.close()
    Enumerator(baos.toByteArray)
  }

}

object MongoS3Compability {
  //Todo: remove ALL following when mongoDB is not in use at all.
  def checkIsTheFileInS3(fileRefId:FileRefId): Boolean = {
    val mongoRegex = "([0-9a-f]{8})\\-([0-9a-f]{4})\\-([0-9a-f]{4})\\-([0-9a-f]{4})\\-([0-9a-f]{12})".r
    fileRefId.value match {
      case mongoRegex(_,_,_,_,_) => false
      case _ => true
    }
  }

  type GetFileResult = GetFileError Xor FileFound
  sealed trait GetFileError
  case object GetFileNotFoundError extends GetFileError

  def retrieveFileFromMongoDB(getFileFromRepo: FileRefId => Future[Option[FileData]])
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
