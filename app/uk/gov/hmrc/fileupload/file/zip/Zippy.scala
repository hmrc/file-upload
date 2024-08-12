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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, File, EnvelopeStatusClosed, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindResult, FindServiceError}

import scala.concurrent.{ExecutionContext, Future}

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
