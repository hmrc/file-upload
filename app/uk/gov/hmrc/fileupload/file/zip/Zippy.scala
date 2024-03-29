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

package uk.gov.hmrc.fileupload.file.zip

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.streams.IterateeStreams
import uk.gov.hmrc.fileupload.file.zip.ZipStream.{ZipFileInfo, ZipStreamEnumerator}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, File, EnvelopeStatusClosed, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileName}

import java.io.{BufferedOutputStream, ByteArrayOutputStream}
import java.util.UUID
import java.util.zip.ZipOutputStream
import scala.concurrent.{ExecutionContext, Future}

/* In order to move off play-iteratees (and Play 2.8), we can replace this manual zipping with a
 * call to file-upload-frontend (/internal-file-upload/zip/envelopes/:envelopeId) which
 * will return a pre-signed URL to the zipped file - this can then be streamed back.
 *
 * The difference however is that the zip returned from the frontend is compressed, where
 * as this implementation is not. So we would have to check that no clients would have
 * an issue with this.
*/
object Zippy {

  private val logger = Logger(getClass)

  sealed trait ZipEnvelopeError
  case object ZipEnvelopeNotFoundError extends ZipEnvelopeError
  case object EnvelopeNotRoutedYet extends ZipEnvelopeError
  case class ZipProcessingError(message: String) extends ZipEnvelopeError

  private object FilesForZip {
    def unapply(envelope: Envelope): Option[Option[Seq[File]]] =
      envelope.status match {
        case EnvelopeStatusRouteRequested | EnvelopeStatusClosed
               => Some(envelope.files)
        case _ => None
      }
  }

  def zipEnvelopeLegacy(
    getEnvelope   : EnvelopeId => Future[FindResult],
    retrieveS3File: (EnvelopeId, FileId) => Future[Source[ByteString, _]]
  )(envelopeId    : EnvelopeId
  )(implicit
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Either[ZipEnvelopeError, Enumerator[Array[Byte]]]] =
    getEnvelope(envelopeId).map {
      case Right(envelopeWithFiles @ FilesForZip(Some(files))) =>
        val zipFiles = files.collect {
          case f =>
            val fileName = f.name.getOrElse(FileName(UUID.randomUUID().toString))
            logger.info(s"""zipEnvelope: envelopeId=${envelopeWithFiles._id} fileId=${f.fileId} fileRefId=${f.fileRefId} length=${f.length.getOrElse(-1)} uploadDate=${f.uploadDate.getOrElse("-")}""")
            ZipFileInfo(
              fileName,
              isDir = false,
              new java.util.Date(),
              Some(() =>
                retrieveS3File(envelopeWithFiles._id, f.fileId)
                  .map { source =>
                    IterateeStreams.publisherToEnumerator(
                      source
                        .via(Flow[ByteString].map(_.toArray))
                        .runWith(Sink.asPublisher(fanout = false))
                    )
                 }
              )
            )
        }
        Right(ZipStreamEnumerator(zipFiles))

      case Right(envelopeWithoutFiles @ FilesForZip(None)) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope was empty - returning empty ZIP file.")
        Right(emptyZip())

      case Right(envelopeWithWrongStatus: Envelope) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope has wrong status [${envelopeWithWrongStatus.status}], returned error")
        Left(EnvelopeNotRoutedYet)

      case Left(FindEnvelopeNotFoundError) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope not found, returned error")
        Left(ZipEnvelopeNotFoundError)

      case Left(FindServiceError(message)) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Other error [$message]")
        Left(ZipProcessingError(message))
    }

  private def emptyZip(): Enumerator[Array[Byte]] = {
    val baos = new ByteArrayOutputStream()
    val out =  new ZipOutputStream(new BufferedOutputStream(baos))
    out.close()
    Enumerator(baos.toByteArray)
  }

  def zipEnvelope(
    getEnvelope: EnvelopeId => Future[FindResult],
    downloadZip: Envelope   => Future[Source[ByteString, NotUsed]]
  )(
    envelopeId: EnvelopeId
  )(implicit
    ec: ExecutionContext
  ): Future[Either[ZipEnvelopeError, Source[ByteString, NotUsed]]] =
    getEnvelope(envelopeId).flatMap {
      case Right(envelopeWithFiles @ FilesForZip(Some(_))) =>
        downloadZip(envelopeWithFiles)
          .map(Right.apply)
          .recover { case ex => Left(ZipProcessingError(ex.getMessage)) }

      case Right(envelopeWithoutFiles @ FilesForZip(None)) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope was empty - returning empty ZIP file.")
        Future.successful(Right(Source.empty))

      case Right(envelopeWithWrongStatus: Envelope) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope has wrong status [${envelopeWithWrongStatus.status}], returned error")
        Future.successful(Left(EnvelopeNotRoutedYet))

      case Left(FindEnvelopeNotFoundError) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Envelope not found, returned error")
        Future.successful(Left(ZipEnvelopeNotFoundError))

      case Left(FindServiceError(message)) =>
        logger.warn(s"Retrieving zipped envelope [$envelopeId]. Other error [$message]")
        Future.successful(Left(ZipProcessingError(message)))
    }
}
