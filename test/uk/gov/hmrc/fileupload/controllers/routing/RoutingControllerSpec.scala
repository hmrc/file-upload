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
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.ApplicationModule
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, TestApplicationComponents}

import scala.concurrent.{ExecutionContext, Future}

class RoutingControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with TestApplicationComponents
     with ScalaFutures
     with OptionValues {

  given ExecutionContext = ExecutionContext.global
  import uk.gov.hmrc.fileupload.Support.StreamImplicits.given

  val failed = Future.failed(Exception("not good"))

  def newController(
    handleCommand: EnvelopeCommand => Future[Either[CommandNotAccepted, CommandAccepted.type]] = _ => failed,
    newId        : () => String                                                                = () => "testId"
  ) = {
    val appModule = mock[ApplicationModule]
    when(appModule.envelopeCommandHandler).thenReturn(handleCommand)
    when(appModule.newId).thenReturn(newId)
    RoutingController(appModule, app.injector.instanceOf[ControllerComponents])
  }

  val destination = "testDestination"
  val envelopeId = EnvelopeId()
  val validRequest = FakeRequest().withBody(Json.toJson(RouteEnvelopeRequest(envelopeId, "application", destination)))

  "Create Routing Request" should {
    "return 201 response (happy path)" in {
      val routingRequestId = "foo"
      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)),
        newId = () => routingRequestId)

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe CREATED
      header(LOCATION, result).value shouldBe uk.gov.hmrc.fileupload.controllers.routing.routes.RoutingController.routingStatus(routingRequestId).url
    }

    "return 400 bad request if routing envelope already requested" in {
      val controller = newController(handleCommand = _ => Future.successful(
        Left(EnvelopeRoutingAlreadyRequestedError)
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(s"Routing request already received for envelope: $envelopeId")
    }

    "return 400 bad request if envelope already sealed" in {
      val controller = newController(handleCommand = _ => Future.successful(
        Left(EnvelopeSealedError)
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(s"Routing request already received for envelope: $envelopeId")
    }

    "return 400 bad request if files contained errors" in {
      val controller = newController(handleCommand = _ => Future.successful(
        Left(FilesWithError(List(FileId("id1") ,FileId("id2"))))
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(s"Files: [id1, id2] contain errors")
    }

    "return 400 bad request if envelope was deleted or doesn't exist" in {
      val controller = newController(handleCommand = _ => Future.successful(
        Left(EnvelopeNotFoundError)
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(s"Envelope with id: $envelopeId not found")
    }

    "return 400 bad request if sealing was not possible for other reason" in {
      val errorMsg = "errorMsg"
      val controller =
        newController(handleCommand =
           _ => Future.successful(
                  Left(new CommandNotAccepted { override def toString = errorMsg })
                )
        )

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(errorMsg)
    }

    "return 400 bad request if sealing was not possible for envelope item count exceeds is less than actual number of files" in {
      val errorMsg = "Envelope item count exceeds maximum of 3, actual: 4"
      val controller = newController(handleCommand = _ => Future.successful(
        Left(EnvelopeItemCountExceededError(allowedItemCount = 3, actualItemCount = 4))
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(errorMsg)
    }

    "return 400 bad request if sealing was not possible for envelope size exceeds maximum has been reached" in {
      val errorMsg = "Envelope size exceeds maximum of 10.00 MB"
      val controller = newController(handleCommand = _ => Future.successful(
        Left(EnvelopeMaxSizeExceededError(maxSizeAllowed = 10 * 1024 * 1024))
      ))

      val result = controller.createRoutingRequest()(validRequest)

      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(errorMsg)
    }
  }
}
