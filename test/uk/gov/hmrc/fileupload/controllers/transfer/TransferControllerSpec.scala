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

package uk.gov.hmrc.fileupload.controllers.transfer

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.controllers.AuthBasicModule
import uk.gov.hmrc.fileupload.read.envelope.Envelope
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class TransferControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def newController(withBasicAuth: AuthBasicModule = new AuthBasicModule {
                      override def userAuthorised(credentials: Option[String]): Boolean = true
                    },getEnvelopesByDestination: Option[String] => Future[List[Envelope]] = _ => failed,
                    handleCommand: EnvelopeCommand => Future[Xor[CommandNotAccepted, CommandAccepted.type]] = _ => failed) =
    new TransferController(withBasicAuth, getEnvelopesByDestination, handleCommand, null)


  "Delete envelope" should {
    "return response with 200" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => Future.successful(Xor.Right(CommandAccepted)))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.OK
    }

    "return response with 500" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => Future.successful(Xor.Left(CommandError("not good"))))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return response with 404" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => Future.successful(Xor.Left(EnvelopeNotFoundError)))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }

    "return response with 410" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => Future.successful(Xor.Left(EnvelopeArchivedError)))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.GONE
    }

    "return response with 423" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => Future.successful(Xor.Left(EnvelopeSealedError)))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.LOCKED
    }

    "return response with 503" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(handleCommand = _ => failed)
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.SERVICE_UNAVAILABLE
    }
  }
}
