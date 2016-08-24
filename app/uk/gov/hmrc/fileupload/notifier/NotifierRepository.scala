/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.notifier

import cats.data.Xor
import play.api.Play.current
import play.api.http.Status
import play.api.libs.json.{Format, Json}
import play.api.libs.ws.{WS, WSRequestHolder, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object NotifierRepository {

  type NotifyResult = Xor[NotifyError, EnvelopeId]

  case class Notification(envelopeId: EnvelopeId, fileId: FileId, status: String, reason: Option[String])

  implicit val notificationFormats: Format[Notification] = Json.format[Notification]

  sealed trait NotifyError
  case class NoConsumerRegisteredError(envelopeId: EnvelopeId, fileId: FileId) extends NotifyError
  case class NotificationFailedError(envelopeId: EnvelopeId, fileId: FileId, reason: String) extends NotifyError

  def notify(httpCall: (WSRequestHolder => Future[Xor[PlayHttpError, WSResponse]]))
            (notification: Notification, url: String)
            (implicit executionContext: ExecutionContext): Future[NotifyResult] =
    httpCall(WS
      .url(s"$url")
      .withBody(Json.stringify(Json.toJson(notification)))
      .withMethod("POST")).map {
      case Xor.Left(error) => Xor.left(NotificationFailedError(notification.envelopeId, notification.fileId, error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(notification.envelopeId)
        case _ => Xor.left(NotificationFailedError(notification.envelopeId, notification.fileId, response.body))
      }
    }
}
