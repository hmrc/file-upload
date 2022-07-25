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

import org.mockito.MockitoSugar
import org.scalatest.concurrent.{ScalaFutures, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.{ApplicationModule, TestApplicationComponents}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future}

class SDESCallbackControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with TestApplicationComponents
     with MockitoSugar
     with ScalaFutures
     with IntegrationPatience {

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def newController(handleCommand: EnvelopeCommand => Future[Either[CommandNotAccepted, CommandAccepted.type]] = _ => failed
  ) = {
    val appModule = mock[ApplicationModule]
    when(appModule.envelopeCommandHandler).thenReturn(handleCommand)
    new SDESCallbackController(appModule, app.injector.instanceOf[ControllerComponents])
  }

  "callback" should {

    val notificationTypesJustReturn200s = Seq(FileReady, FileProcessingFailure)

    notificationTypesJustReturn200s.foreach { notification =>
      s"return response with 200 if notification is ${notification.value}" in {
        val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

        val controller = newController(handleCommand = _ => Future.successful(Left(CommandError("this shouldn't be called"))))
        val result = controller.callback()(request).futureValue

        result.header.status shouldBe Status.OK
      }
    }

    val notificationTypesToBeProcessed = Seq(FileReceived, FileProcessed)

    notificationTypesToBeProcessed.foreach { notification =>
      s"return response with 500 if handler returns error for ${notification.value}" in {
        val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

        val controller = newController(handleCommand = _ => Future.successful(Left(CommandError("not good"))))
        val result = controller.callback()(request).futureValue

        result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
      }

      s"return BadRequest if EnvelopeId not found for ${notification.value}" in {
        val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

        val controller = newController(handleCommand = _ => Future.successful(Left(EnvelopeNotFoundError)))
        val result = controller.callback()(request).futureValue

        result.header.status shouldBe Status.BAD_REQUEST
      }

      s"return response with 503 for ${notification.value}" in {
        val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

        val controller = newController(handleCommand = _ => failed)
        val result = controller.callback()(request).futureValue

        result.header.status shouldBe Status.SERVICE_UNAVAILABLE
      }
    }

    "return 200 response if handling duplicate request for FileProcessed" in {
      val request = FakeRequest().withBody(Json.toJson(notificationItem(FileProcessed)))

      val controller = newController(handleCommand = _ => Future.successful(Left(EnvelopeArchivedError)))
      val result = controller.callback()(request).futureValue

      result.header.status shouldBe Status.OK
    }

    "return 200 response if handling duplicate request for FileReceived" in {
      val request = FakeRequest().withBody(Json.toJson(notificationItem(FileReceived)))

      val controller = newController(handleCommand = _ => Future.successful(Left(EnvelopeAlreadyRoutedError)))
      val result = controller.callback()(request).futureValue

      result.header.status shouldBe Status.OK
    }
  }

  private def notificationItem(notification: Notification) =
    NotificationItem(
      notification      = notification,
      informationType   = Some("S18"),
      filename          = "ourref.xyz.doc",
      checksumAlgorithm = MD5,
      checksum          = "83HQWQ93D909Q0QWIJQE39831312EUIUQIWOEU398931293DHDAHBAS",
      correlationId     = "d1800c47-29b0-440a-9e2e-9d7362795e10",
      availableUntil    = Some(Instant.now()),
      failureReason     = Some("We were unable to successfully do some action"),
      dateTime          = Instant.now()
    )
}
