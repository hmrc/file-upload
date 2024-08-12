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

import org.mockito.Mockito.when
import org.scalatest.concurrent.{ScalaFutures, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.{ApplicationModule, TestApplicationComponents}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

class SDESCallbackControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with TestApplicationComponents
     with MockitoSugar
     with ScalaFutures
     with IntegrationPatience {

  given ExecutionContext = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def newController(handleCommand: EnvelopeCommand => Future[Either[CommandNotAccepted, CommandAccepted.type]] = _ => failed
  ) = {
    val appModule = mock[ApplicationModule]
    when(appModule.envelopeCommandHandler).thenReturn(handleCommand)
    SDESCallbackController(appModule, app.injector.instanceOf[ControllerComponents])
  }

  "callback" should {
    val notificationTypesJustReturn200s = Seq(Notification.FileReady)

    notificationTypesJustReturn200s.foreach { notification =>
      s"return response with 200 if notification is ${notification.value}" in {
        val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

        val controller = newController(handleCommand = _ => Future.successful(Left(CommandError("this shouldn't be called"))))
        val result = controller.callback()(request).futureValue

        result.header.status shouldBe Status.OK
      }
    }

    val notificationTypesToBeProcessed = Seq(
      Notification.FileReceived          -> Some(EnvelopeAlreadyRoutedError),
      Notification.FileProcessed         -> Some(EnvelopeArchivedError),
      Notification.FileProcessingFailure -> None
    )

    notificationTypesToBeProcessed.foreach { case (notification, duplicateError) =>
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

      duplicateError.foreach { duplicateError =>
        s"return 200 response if handling duplicate request for ${notification.value}" in {
          val request = FakeRequest().withBody(Json.toJson(notificationItem(notification)))

          val controller = newController(handleCommand = _ => Future.successful(Left(duplicateError)))
          val result = controller.callback()(request).futureValue

          result.header.status shouldBe Status.OK
        }
      }
    }

    "suppress urls in reason field" in {
      val notification =
        notificationItem(Notification.FileProcessingFailure)
          .copy(failureReason = Some("Could not download https://localhost:8080/asd"))
      val request = FakeRequest().withBody(Json.toJson(notification))

      val res = AtomicReference[String]()
      val controller = newController(handleCommand = command => {
        command match {
          case MarkEnvelopeAsRouted(_, _, Some(reason)) => res.set(reason)
          case _ =>
        }
        Future.successful(Right(CommandAccepted))
      })

      val result = controller.callback()(request).futureValue
      result.header.status shouldBe Status.OK

      Option(res.get) shouldBe Some("downstream_processing_failure: Could not download {SUPPRESSED_URL}")
    }
  }

  private def notificationItem(notification: Notification) =
    NotificationItem(
      notification      = notification,
      informationType   = Some("S18"),
      filename          = "ourref.xyz.doc",
      checksumAlgorithm = ChecksumAlgorithm.MD5,
      checksum          = "83HQWQ93D909Q0QWIJQE39831312EUIUQIWOEU398931293DHDAHBAS",
      correlationId     = "d1800c47-29b0-440a-9e2e-9d7362795e10",
      availableUntil    = Some(Instant.now()),
      failureReason     = Some("We were unable to successfully do some action"),
      dateTime          = Instant.now()
    )
}
