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

package uk.gov.hmrc.fileupload.controllers.routing

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeCommand
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.http.HeaderNames._

import scala.concurrent.{ExecutionContext, Future}

class RoutingControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def newController(handleCommand: EnvelopeCommand => Future[Xor[CommandNotAccepted, CommandAccepted.type]] = _ => failed,
                    newId: () => String = () => "testId") =
    new RoutingController(handleCommand, newId)

  val validRequest = FakeRequest().withBody(Json.toJson(RouteEnvelopeRequest(EnvelopeId(), "application", "destination")))

  "Create Routing Request" should {
    "return 201 response (happy path)" in {
      val routingRequestId = "foo"
      val controller = newController(handleCommand = _ => Future.successful(Xor.Right(CommandAccepted)),
        newId = () => routingRequestId)

      val result = controller.createRoutingRequest()(validRequest).futureValue

      result.header.status shouldBe Status.CREATED
      result.header.headers(LOCATION) shouldBe routes.RoutingController.routingStatus(routingRequestId).url
    }
    "return 400 bad request if sealing was not possible" in {
      val errorMsg = "errorMsg"
      val controller = newController(handleCommand = _ => Future.successful(
        Xor.Left(new CommandNotAccepted {override def toString = errorMsg })
      ))

      val result = controller.createRoutingRequest()(validRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
      bodyOf(result) should include(errorMsg)
    }
  }

}
