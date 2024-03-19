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

package uk.gov.hmrc.fileupload

import akka.actor.ActorSystem
import org.joda.time.DateTime
import play.api.http.HttpEntity
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.EnvelopeReport
import uk.gov.hmrc.fileupload.read.envelope._

import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Support {

  object StreamImplicits {
    implicit val system = ActorSystem()
  }

  def consume(data: HttpEntity) = {
    import StreamImplicits.system
    Await.result(data.consumeData, 500.millis).toArray
  }

  def envelope = new Envelope(
    _id         = EnvelopeId(),
    constraints = None,
    callbackUrl = Some("http://absolute.callback.url"),
    expiryDate  = Some(DateTime.now().plusDays(1).withMillisOfSecond(0)),
    metadata    = Some(Json.obj("anything" -> "the caller wants to add to the envelope")),
    destination = Some("destination"),
    application = Some("application")
  )

  def envelopeWithAFile(fileId: FileId) =
    envelope.copy(files = Some(List(File(fileId, fileRefId = FileRefId("ref"), status = FileStatusQuarantined))))

  val envelopeBody =
    Json.toJson[Envelope](envelope)

  def envelopeReport = EnvelopeReport(callbackUrl = Some("http://absolute.callback.url"))

  val envelopeReportBody =
    Json.toJson(envelopeReport)

  def expiredEnvelope =
    envelope.copy(expiryDate = Some(DateTime.now().minusMinutes(3)))

  def farInTheFutureEnvelope =
    envelope.copy(expiryDate = Some(DateTime.now().plusDays(3)))

  val envelopeNotFound: WithValidEnvelope =
    new WithValidEnvelope(
      _ => Future.successful(None)
    )

  def fileRefId(): FileRefId =
    FileRefId(UUID.randomUUID().toString)
}
