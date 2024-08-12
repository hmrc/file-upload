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
  cc       : ControllerComponents
)(using ExecutionContext
) extends BackendController(cc):

  private val logger = Logger(getClass)

  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] =
    appModule.envelopeCommandHandler

  def callback() =
    Action.async(parse.json): request =>
      given Request[JsValue] = request
      withJsonBody[NotificationItem]: item =>
        val envelopeId = EnvelopeId(item.correlationId)
        item.notification match
          case Notification.FileReceived          => logger.info(s"Received FileReceived SDES callback: $item")
                                                     tryMarkAsRouted(envelopeId) // we will stop any push retries
          case Notification.FileProcessingFailure => val reason = item.failureReason.map(santiseReason)
                                                     logger.info(s"Received FileProcessingFailure SDES callback: ${item.copy(failureReason = None)}${reason.fold("")(", reason: " + _)}")
                                                     tryMarkAsRouted(envelopeId, reason = Some("downstream_processing_failure" + reason.fold("")(": " + _)))
          case Notification.FileProcessed         => logger.info(s"Received FileProcessed SDES callback: $item")
                                                     tryArchive(envelopeId)
          case _                                  => // we have no retry mechanisms built that will retry if we're notified of an error here.
                                                     logger.info(s"Received SDES callback: $item")
                                                     Future.successful(Ok)

  private val urlRegex = "https?:[^\\s'\"]*".r

  private def santiseReason(reason: String): String =
    urlRegex.replaceAllIn(reason, "{SUPPRESSED_URL}")

  private def tryMarkAsRouted(envelopeId: EnvelopeId, reason: Option[String] = None) =
    handleCommand(MarkEnvelopeAsRouted(envelopeId, isPushed = true, reason = reason))
      .map:
        case Right(_) => Ok
        case Left(EnvelopeAlreadyRoutedError) =>
          logger.info(s"Received another request to route envelope [$envelopeId]. It was previously routed.")
          Ok
        case Left(EnvelopeArchivedError) =>
          logger.info(s"Received a request to route envelope [$envelopeId]. It was previously archived.")
          Ok
        case Left(EnvelopeNotFoundError) => ExceptionHandler(BAD_REQUEST, s"CorrelationId $envelopeId not found")
        case Left(CommandError(m))       => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
        case Left(_)                     => ExceptionHandler(INTERNAL_SERVER_ERROR, s"Envelope with id: $envelopeId locked")
      .recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }

  private def tryArchive(envelopeId: EnvelopeId) =
    handleCommand(ArchiveEnvelope(envelopeId))
      .map:
        case Right(_)                    => Ok
        case Left(EnvelopeArchivedError) =>
          logger.info(s"Received another request to delete envelope [$envelopeId]. It was previously deleted.")
          Ok
        case Left(EnvelopeNotFoundError) => ExceptionHandler(BAD_REQUEST          , s"CorrelationId $envelopeId not found")
        case Left(CommandError(m))       => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
        case Left(_)                     => ExceptionHandler(INTERNAL_SERVER_ERROR, s"Envelope with id: $envelopeId locked")
      .recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }

  /**
   * This method has been overridden to provide additional logging, as it'll make life easier
   * if the interface with SDES isn't accurately described or implemented.
   * When we're happy with the API being stable, we can delete this override.
   */
  override protected def withJsonBody[T](f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] =
    Try(request.body.validate[T]) match
      case Success(JsSuccess(payload, _)) =>
        f(payload)
      case Success(JsError(errs)) =>
        logger.warn(s"Failed to parse ${m.runtimeClass.getSimpleName} payload. Errors: [$errs] Payload: [${request.body}]")
        Future.successful(BadRequest(s"Invalid ${m.runtimeClass.getSimpleName} payload: $errs"))
      case Failure(e) =>
        logger.warn(s"Completely failed to parse body due to ${e.getMessage}. Payload [${request.body}]")
        Future.successful(BadRequest(s"could not parse body due to ${e.getMessage}"))

end SDESCallbackController

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

object NotificationItem:

  private def toResult[A](e: Either[String, A]): JsResult[A] =
    e match
      case Right(r) => JsSuccess(r)
      case Left(l) => JsError(__, l)

  given Format[Notification] =
    new Format[Notification]:
      override def reads(js: JsValue): JsResult[Notification] =
        js.validate[String]
          .flatMap(s => toResult(Notification.parse(s)))

      override def writes(n: Notification): JsValue =
        JsString(n.value)

  given Format[ChecksumAlgorithm] =
    new Format[ChecksumAlgorithm]:
      override def reads(js: JsValue): JsResult[ChecksumAlgorithm] =
        js.validate[String]
          .flatMap(s => toResult(ChecksumAlgorithm.parse(s)))

      override def writes(c: ChecksumAlgorithm): JsValue =
        JsString(c.value)

  given OFormat[NotificationItem] =
    ( (__ \ "notification"     ).format[Notification]
    ~ (__ \ "informationType"  ).formatNullable[String]
    ~ (__ \ "filename"         ).format[String]
    ~ (__ \ "checksumAlgorithm").format[ChecksumAlgorithm]
    ~ (__ \ "checksum"         ).format[String]
    ~ (__ \ "correlationID"    ).format[String]
    ~ (__ \ "availableUntil"   ).formatNullable[Instant]
    ~ (__ \ "failureReason"    ).formatNullable[String]
    ~ (__ \ "dateTime"         ).format[Instant]
    )(NotificationItem.apply, ni => Tuple.fromProductTyped(ni))

end NotificationItem

enum Notification(
  val value: String
):
  case FileReady             extends Notification("FileReady"            )
  case FileReceived          extends Notification("FileReceived"         )
  case FileProcessingFailure extends Notification("FileProcessingFailure")
  case FileProcessed         extends Notification("FileProcessed"        )

object Notification:
  def parse(s: String): Either[String, Notification] =
    values
      .find(_.value == s)
      .toRight(s"Invalid notification - should be one of ${values.map(_.value).mkString(", ")}")

enum ChecksumAlgorithm(
  val value: String
):
  case MD5  extends ChecksumAlgorithm("md5" )
  case SHA1 extends ChecksumAlgorithm("SHA1")
  case SHA2 extends ChecksumAlgorithm("SHA2")


object ChecksumAlgorithm:
  def parse(s: String): Either[String, ChecksumAlgorithm] =
    values
      .find(_.value == s)
      .toRight(s"Invalid notification - should be one of ${values.map(_.value).mkString(", ")}")
