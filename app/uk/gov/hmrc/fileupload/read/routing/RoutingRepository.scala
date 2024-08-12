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

package uk.gov.hmrc.fileupload.read.routing

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{__, Json, JsObject, Writes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.read.envelope.Envelope

import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

object RoutingRepository {
  private val logger = Logger(getClass)

  type PushResult = Either[PushError, Unit]
  case class PushError(correlationId: String, reason: String)

  type BuildNotificationResult = Either[BuildNotificationError, FileTransferNotification]
  case class BuildNotificationError(envelopeId: EnvelopeId, reason: String, isTransient: Boolean)

  implicit val ftnw: Writes[FileTransferNotification] = FileTransferNotification.format
  implicit val zrw : Writes[ZipRequest] = ZipRequest.writes

  def pushFileTransferNotification(
    httpCall     : WSRequest => Future[Either[PlayHttpError, WSResponse]],
    wSClient     : WSClient,
    routingConfig: RoutingConfig
  )(fileTransferNotification: FileTransferNotification
  )(implicit
    ec          : ExecutionContext
  ): Future[PushResult] =
    import play.api.libs.ws.writeableOf_JsValue
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
    getZipData   : Envelope => Future[Option[ZipData]],
    routingConfig: RoutingConfig
  )(envelope     : Envelope
  )(implicit
    ec           : ExecutionContext
  ): Future[BuildNotificationResult] =
    getZipData(envelope).map {
      case Some(zipData) => Right(createNotification(envelope, zipData, routingConfig))
      case None          => Left(BuildNotificationError(envelope._id, "Files to zip are no-longer available", isTransient = false))
    }
    .recover {
      case ex            => logger.error(s"Could not build file transfer notification: ${ex.getMessage}", ex)
                            Left(BuildNotificationError(envelope._id, ex.getMessage, isTransient = true))
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
    ~ (__ \ "url"        ).format[String].inmap(DownloadUrl.apply, _.value)
    )(ZipData.apply, zd => Tuple.fromProductTyped(zd))
}
