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

import cats.data.Xor
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.EnvelopeId

import scala.concurrent.{ExecutionContext, Future}

object RoutingRepository {

  type PushResult = Xor[PushError, Unit]
  case class PushError(correlationId: String, pushUrl: String, reason: String)

  implicit val ftnw = FileTransferNotification.writes

  def pushFileTransferNotification(
    httpCall: WSRequest => Future[Xor[PlayHttpError, WSResponse]],
    wSClient: WSClient
  )(fileTransferNotification: FileTransferNotification,
    pushUrl : String
  )(implicit executionContext: ExecutionContext
  ): Future[PushResult] =
    httpCall(
      wSClient
        .url(pushUrl)
        .withHeaders("User-Agent" -> "file-upload")
        .withBody(Json.toJson(fileTransferNotification))
        .withMethod("POST")
    ).map {
      case Xor.Left(error) => Xor.left(PushError(fileTransferNotification.audit.correlationId, pushUrl, error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(())
        case _ => Xor.left(PushError(fileTransferNotification.audit.correlationId, pushUrl, s"${response.status} ${response.body}"))
      }
    }

  def buildFileTransferNotification(
    routingConfig: RoutingConfig
  )(envelopeId: EnvelopeId
  ): Future[FileTransferNotification] = Future.successful {
    // TODO we may need to call the frontend to generate a pre-signed URL instead...
    // especially since we need to prezip to calculate size and checsum...
    val host = routingConfig.host
    val downloadCall = uk.gov.hmrc.fileupload.controllers.transfer.routes.TransferController.download(envelopeId)

    val file = File(
      recipientOrSender = Some("String"), // TODO
      name              = "String", // TODO
      location          = Some(s"https://$host$downloadCall"),
      checksum          = Checksum(Algorithm.Md5, "0"), // TODO
      size              = 0, // TODO
      properties        = List.empty[Property]
    )

    FileTransferNotification(
      informationType = "String",  // TODO
      file            = file,
      audit           = Audit(correlationId = envelopeId.value)
    )
  }
}
