/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers.routing

import java.time.Instant

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.{ControllerComponents, Request, Result}
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId}
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler
import uk.gov.hmrc.fileupload.write.envelope.{ArchiveEnvelope, EnvelopeArchivedError, EnvelopeAlreadyRoutedError, EnvelopeCommand, EnvelopeNotFoundError, MarkEnvelopeAsRouted}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class SDESCallbackController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  private val logger = Logger(getClass)

  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] =
    appModule.envelopeCommandHandler

  def callback() = Action.async(parse.json) { implicit request =>
    withJsonBody[NotificationItem] { item =>
      logger.info(s"Received SDES callback: $item")
      val envelopeId = EnvelopeId(item.correlationId)
      item.notification match {
        case FileReceived          => tryMarkAsRouted(envelopeId) // we will stop any push retries
        case FileProcessed         => tryArchive(envelopeId)
        case FileProcessingFailure => tryArchive(envelopeId, reason = Some("downstream_processing_failure" + item.failureReason.fold("")(": " + _)))
        case _                     => // we have no retry mechanisms built that will retry if we're notified of an error here.
                                      Future.successful(Ok)
      }
    }
  }

  private def tryMarkAsRouted(envelopeId: EnvelopeId) =
    handleCommand(MarkEnvelopeAsRouted(envelopeId, isPushed = true)).map {
      case Right(_) => Ok
      case Left(EnvelopeAlreadyRoutedError) =>
        logger.info(s"Received another request to route envelope [$envelopeId]. It was previously routed.")
        Ok
      case Left(EnvelopeNotFoundError) => ExceptionHandler(BAD_REQUEST, s"CorrelationId $envelopeId not found")
      case Left(CommandError(m))       => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Left(_)                     => ExceptionHandler(INTERNAL_SERVER_ERROR, s"Envelope with id: $envelopeId locked")
    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }

  private def tryArchive(envelopeId: EnvelopeId, reason: Option[String] = None) =
    handleCommand(ArchiveEnvelope(envelopeId, reason)).map {
      case Right(_) => Ok
      case Left(EnvelopeArchivedError) =>
        logger.info(s"Received another request to delete envelope [$envelopeId]. It was previously deleted.")
        Ok
      case Left(EnvelopeNotFoundError) => ExceptionHandler(BAD_REQUEST          , s"CorrelationId $envelopeId not found")
      case Left(CommandError(m))       => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Left(_)                     => ExceptionHandler(INTERNAL_SERVER_ERROR, s"Envelope with id: $envelopeId locked")
    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }

  /**
   * This method has been overridden to provide additional logging, as it'll make life easier
   * if the interface with SDES isn't accurately described or implemented.
   * When we're happy with the API being stable, we can delete this override.
   */
  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) =>
        logger.warn(s"Failed to parse ${m.runtimeClass.getSimpleName} payload. Errors: [$errs] Payload: [${request.body}]")
        Future.successful(BadRequest(s"Invalid ${m.runtimeClass.getSimpleName} payload: $errs"))
      case Failure(e) =>
        logger.warn(s"Completely failed to parse body due to ${e.getMessage}. Payload [${request.body}]")
        Future.successful(BadRequest(s"could not parse body due to ${e.getMessage}"))
    }
}

case class NotificationItem(
  notification     : Notification,
  informationType  : Option[String],
  filename         : String,
  checksumAlgorithm: ChecksumAlgorithm,
  checksum         : String,
  correlationId    : String,
  availableUntil   : Option[Instant],
  failureReason    : Option[String],
  dateTime         : Instant
)

object NotificationItem {

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match {
      case Right(r) => JsSuccess(r)
      case Left(l) => JsError(__, l)
    }

  implicit val notificationFormat: Format[Notification] = new Format[Notification] {
    override def reads(js: JsValue): JsResult[Notification] =
      js.validate[String]
        .flatMap(s => toResult(Notification.parse(s)))

    override def writes(n: Notification): JsValue =
      JsString(n.value)
  }

  implicit val checksumAlgorithmFormat: Format[ChecksumAlgorithm] = new Format[ChecksumAlgorithm] {
    override def reads(js: JsValue): JsResult[ChecksumAlgorithm] =
      js.validate[String]
        .flatMap(s => toResult(ChecksumAlgorithm.parse(s)))

    override def writes(c: ChecksumAlgorithm): JsValue =
      JsString(c.value)
  }

  implicit val format: OFormat[NotificationItem] =
    ( (__ \ "notification"     ).format[Notification]
    ~ (__ \ "informationType"  ).formatNullable[String]
    ~ (__ \ "filename"         ).format[String]
    ~ (__ \ "checksumAlgorithm").format[ChecksumAlgorithm]
    ~ (__ \ "checksum"         ).format[String]
    ~ (__ \ "correlationID"    ).format[String]
    ~ (__ \ "availableUntil"   ).formatNullable[Instant]
    ~ (__ \ "failureReason"    ).formatNullable[String]
    ~ (__ \ "dateTime"         ).format[Instant]
    )(NotificationItem.apply, unlift(NotificationItem.unapply))
}

sealed trait Notification {
  val value: String
}

case object FileReady extends Notification {
  val value = "FileReady"
}

case object FileReceived extends Notification {
  val value = "FileReceived"
}

case object FileProcessingFailure extends Notification {
  val value = "FileProcessingFailure"
}

case object FileProcessed extends Notification {
  val value = "FileProcessed"
}

object Notification {
  private val values = List(FileReady, FileReceived, FileProcessingFailure, FileProcessed)

  def parse(s: String): Either[String, Notification] =
    values
      .find(_.value == s)
      .toRight(s"Invalid notification - should be one of ${values.map(_.value).mkString(", ")}")
}

sealed trait ChecksumAlgorithm {
  val value: String
}

case object MD5 extends ChecksumAlgorithm {
  val value = "md5"
}

case object SHA1 extends ChecksumAlgorithm {
  val value = "SHA1"
}

case object SHA2 extends ChecksumAlgorithm {
  val value = "SHA2"
}

object ChecksumAlgorithm {
  private val values = List(MD5, SHA1, SHA2)

  def parse(s: String): Either[String, ChecksumAlgorithm] =
    values
      .find(_.value == s)
      .toRight(s"Invalid notification - should be one of ${values.map(_.value).mkString(", ")}")
}
