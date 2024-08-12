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

package uk.gov.hmrc.fileupload.read.notifier

import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object NotifierRepository {

  type NotifyResult = Either[NotifyError, EnvelopeId]

  case class Notification(
    envelopeId: EnvelopeId,
    fileId: FileId,
    status: String,
    reason: Option[String]
  )

  implicit val notificationFormats: Format[Notification] = Json.format[Notification]

  case class NotifyError(envelopeId: EnvelopeId, fileId: FileId, reason: String)

  def notify(
    httpCall: WSRequest => Future[Either[PlayHttpError, WSResponse]],
    wSClient: WSClient
  )(
    notification: Notification,
    url         : String
  )(implicit
    ec: ExecutionContext
  ): Future[NotifyResult] =
    import play.api.libs.ws.writeableOf_JsValue
    httpCall(
      wSClient
        .url(s"$url")
        .withHttpHeaders("User-Agent" -> "file-upload")
        .withBody(Json.toJson(notification))
        .withMethod("POST")
    ).map {
      case Left(error) => Left(NotifyError(notification.envelopeId, notification.fileId, error.message))
      case Right(response) => response.status match {
        case Status.OK => Right(notification.envelopeId)
        case _ => Left(NotifyError(notification.envelopeId, notification.fileId, s"${response.status} ${response.body}"))
      }
    }
}
