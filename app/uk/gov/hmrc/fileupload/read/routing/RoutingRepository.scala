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

package uk.gov.hmrc.fileupload.read.routing

import java.net.URL
import java.util.Base64

import cats.data.Xor
import play.api.http.Status
import play.api.libs.json.{__, Json, JsObject, JsSuccess, JsError, Writes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, File}

import scala.concurrent.{ExecutionContext, Future}

object RoutingRepository {

  type PushResult = Xor[PushError, Unit]
  case class PushError(correlationId: String, reason: String)

  type BuildNotificationResult = Xor[BuildNotificationError, FileTransferNotification]
  case class BuildNotificationError(envelopeId: EnvelopeId, reason: String)

  implicit val ftnw = FileTransferNotification.format
  implicit val zrw = ZipRequest.writes
  implicit val zdf = ZipData.format

  def pushFileTransferNotification(
    httpCall     : WSRequest => Future[Xor[PlayHttpError, WSResponse]],
    wSClient     : WSClient,
    routingConfig: RoutingConfig
  )(fileTransferNotification: FileTransferNotification
  )(implicit executionContext: ExecutionContext
  ): Future[PushResult] =
    httpCall(
      wSClient
        .url(routingConfig.pushUrl)
        .withHeaders(
          "X-Client-ID" -> routingConfig.clientId,
          "User-Agent"  -> "file-upload"
         )
        .withBody(Json.toJson(fileTransferNotification))
        .withMethod("POST")
    ).map {
      case Xor.Left(error) => Xor.left(PushError(fileTransferNotification.audit.correlationId, error.message))
      case Xor.Right(response) => response.status match {
        case Status.NO_CONTENT => Xor.right(())
        case _ => Xor.left(PushError(fileTransferNotification.audit.correlationId, s"Unexpected response: ${response.status} ${response.body}"))
      }
    }

  def buildFileTransferNotification(
    httpCall       : WSRequest => Future[Xor[PlayHttpError, WSResponse]],
    wSClient       : WSClient,
    routingConfig  : RoutingConfig,
    frontendBaseUrl: String
  )(envelope: Envelope
  )(implicit executionContext: ExecutionContext
  ): Future[BuildNotificationResult] =
    httpCall(
      wSClient
        .url(s"$frontendBaseUrl/internal-file-upload/zip/envelopes/${envelope._id}")
        .withHeaders("User-Agent" -> "file-upload")
        .withBody(Json.toJson(ZipRequest(files = envelope.files.toList.flatten.map(f => f.fileId -> f.name))))
        .withMethod("POST")
    ).map {
      case Xor.Left(error) => Xor.left(BuildNotificationError(envelope._id, error.message))
      case Xor.Right(response) => response.status match {
          case Status.OK => response.json.validate[ZipData] match {
              case JsSuccess(zipData, _) => Xor.right(createNotification(envelope, zipData))
              case JsError(errors) => Xor.left(BuildNotificationError(envelope._id, s"Could not parse result $errors"))
            }
          case _ => Xor.left(BuildNotificationError(envelope._id, s"Unexpected response: ${response.status} ${response.body}"))
        }
    }

  def createNotification(envelope: Envelope, zipData: ZipData): FileTransferNotification = {
    val file = FileTransferFile(
      recipientOrSender = envelope.sender,
      name              = zipData.name,
      location          = Some(zipData.url.toString),
      checksum          = Checksum(Algorithm.Md5, base64ToHex(zipData.md5Checksum)),
      size              = zipData.size.toInt,
      properties        = List.empty[Property]
    )

    FileTransferNotification(
      informationType = envelope.destination.getOrElse("unknown"), // destination should be defined at this point
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
  import play.api.libs.functional.syntax._
  val writes: Writes[ZipRequest] =
    Writes.at[JsObject](__ \ "files")
    .contramap[ZipRequest](zr => JsObject(zr.files.map { case (fi, optS) => fi.value -> Json.toJson(optS) }.toSeq))
}


case class ZipData(
  name       : String,
  size       : Long,
  md5Checksum: String,
  url        : URL
)
object ZipData {
  import play.api.libs.functional.syntax._
  val format =
    ( (__ \ "name"       ).format[String]
    ~ (__ \ "size"       ).format[Long]
    ~ (__ \ "md5Checksum").format[String]
    ~ (__ \ "url"        ).format[String].inmap[URL](s => new URL(s), _.toString)
    )(ZipData.apply _, unlift(ZipData.unapply))
}
