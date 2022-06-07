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

package uk.gov.hmrc.fileupload.read.routing

import java.net.URL
import java.util.Base64

import play.api.http.Status
import play.api.libs.json.{__, Json, JsObject, JsSuccess, JsError, Writes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.read.envelope.Envelope

import scala.concurrent.{ExecutionContext, Future}

object RoutingRepository {

  type PushResult = Either[PushError, Unit]
  case class PushError(correlationId: String, reason: String)

  type BuildNotificationResult = Either[BuildNotificationError, FileTransferNotification]
  case class BuildNotificationError(envelopeId: EnvelopeId, reason: String, isTransient: Boolean)

  implicit val ftnw = FileTransferNotification.format
  implicit val zrw = ZipRequest.writes
  implicit val zdf = ZipData.format

  def pushFileTransferNotification(
    httpCall     : WSRequest => Future[Either[PlayHttpError, WSResponse]],
    wSClient     : WSClient,
    routingConfig: RoutingConfig
  )(fileTransferNotification: FileTransferNotification
  )(implicit executionContext: ExecutionContext
  ): Future[PushResult] =
    httpCall(
      wSClient
        .url(routingConfig.pushUrl)
        .withHttpHeaders(
          "X-Client-ID" -> routingConfig.clientId,
          "User-Agent"  -> "file-upload"
         )
        .withBody(Json.toJson(fileTransferNotification))
        .withMethod("POST")
    ).map {
      case Left(error) => Left(PushError(fileTransferNotification.audit.correlationId, error.message))
      case Right(response) => response.status match {
        case Status.NO_CONTENT => Right(())
        case _ => Left(PushError(fileTransferNotification.audit.correlationId, s"Unexpected response: ${response.status} ${response.body}"))
      }
    }

  def buildFileTransferNotification(
    httpCall       : WSRequest => Future[Either[PlayHttpError, WSResponse]],
    wSClient       : WSClient,
    routingConfig  : RoutingConfig,
    frontendBaseUrl: String
  )(envelope: Envelope
  )(implicit executionContext: ExecutionContext
  ): Future[BuildNotificationResult] =
    httpCall(
      wSClient
        .url(s"$frontendBaseUrl/internal-file-upload/zip/envelopes/${envelope._id}")
        .withHttpHeaders("User-Agent" -> "file-upload")
        .withBody(Json.toJson(ZipRequest(files = envelope.files.toList.flatten.map(f => f.fileId -> f.name.map(_.value)))))
        .withMethod("POST")
    ).map {
      case Left(error) => Left(BuildNotificationError(envelope._id, error.message, isTransient = true))
      case Right(response) => response.status match {
          case Status.OK => response.json.validate[ZipData] match {
              case JsSuccess(zipData, _) => Right(createNotification(envelope, zipData, routingConfig))
              case JsError(errors) => Left(BuildNotificationError(envelope._id, s"Could not parse result $errors", isTransient = true))
            }
          case Status.GONE => Left(BuildNotificationError(envelope._id, "Files to zip are no-longer available", isTransient = false))
          case _ => Left(BuildNotificationError(envelope._id, s"Unexpected response: ${response.status} ${response.body}", isTransient = true))
        }
    }

  def createNotification(
    envelope     : Envelope,
    zipData      : ZipData,
    routingConfig: RoutingConfig
  ): FileTransferNotification = {
    val file = FileTransferFile(
      recipientOrSender = routingConfig.recipientOrSender,
      name              = zipData.name,
      location          = Some(zipData.url),
      checksum          = Checksum(Algorithm.Md5, base64ToHex(zipData.md5Checksum)),
      size              = zipData.size.toInt,
      properties        = List.empty[Property]
    )

    FileTransferNotification(
      informationType = routingConfig.informationType,
      file            = file,
      audit           = Audit(correlationId = envelope._id.value)
    )
  }

  def base64ToHex(s: String): String = {
    val bytes = Base64.getDecoder.decode(s)
    java.lang.String.format("%032x", new java.math.BigInteger(1, bytes))
  }
}

case class ZipRequest(
  files: List[(FileId, Option[String])]
)

object ZipRequest {
  val writes: Writes[ZipRequest] =
    Writes.at[JsObject](__ \ "files")
      .contramap[ZipRequest](zr => JsObject(zr.files.map { case (fi, optS) => fi.value -> Json.toJson(optS) }.toSeq))
}


case class ZipData(
  name       : String,
  size       : Long,
  md5Checksum: String,
  url        : DownloadUrl
)
object ZipData {
  import play.api.libs.functional.syntax._
  val format =
    ( (__ \ "name"       ).format[String]
    ~ (__ \ "size"       ).format[Long]
    ~ (__ \ "md5Checksum").format[String]
    ~ (__ \ "url"        ).format[String].inmap(DownloadUrl.apply, unlift(DownloadUrl.unapply))
    )(ZipData.apply _, unlift(ZipData.unapply))
}
