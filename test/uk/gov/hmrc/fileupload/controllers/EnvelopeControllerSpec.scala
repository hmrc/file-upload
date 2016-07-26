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

package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AnyContentAsJson, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.envelope.Envelope
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class EnvelopeControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  import Support._

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def newController(createEnvelope: Envelope => Future[Xor[CreateError, Envelope]] = _ => failed,
                    nextId: () => String = () => "abc-def",
                    findEnvelope: String => Future[Xor[FindError, Envelope]] = _ => failed,
                    deleteEnvelope: String => Future[Xor[DeleteError, Envelope]] = _ => failed) =
    new EnvelopeController(createEnvelope, nextId, findEnvelope, deleteEnvelope)

  "Create envelope with a request" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
	    val serverUrl = "http://production.com:8000"

	    val fakeRequest = new FakeRequest[EnvelopeReport]("POST", "/envelope", FakeHeaders(), body = envelopeReport){
		    override lazy val host = serverUrl
	    }

      val envelope = Support.envelope

      val controller = newController(createEnvelope = _ => Future.successful(Xor.right(envelope)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
	    val location = result.header.headers("Location")
	    location shouldBe s"$serverUrl${routes.EnvelopeController.show(s"${envelope._id}").url}"
    }
  }

  "Create envelope with no body" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = new FakeRequest[EnvelopeReport]("POST", "/envelope", FakeHeaders(), body = EnvelopeReport()) {
        override lazy val host = serverUrl
      }

      val envelope = Support.envelope

      val controller = newController(createEnvelope = _ => Future.successful(Xor.right(envelope)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$serverUrl${routes.EnvelopeController.show(s"${envelope._id}").url}"
    }
  }

  "Get Envelope" should {
    "return an  envelope resource when request id is valid" in {
      val envelope = Support.envelope
      val request = FakeRequest("GET", s"/envelope/${envelope._id}")

      val controller = newController(findEnvelope = _ => Xor.right(envelope))
      val result = controller.show(envelope._id)(request).futureValue

      val actualResponse = Json.parse(consume(result.body))

      import EnvelopeReport._
      val expectedResponse = Json.toJson(EnvelopeReport.fromEnvelope(envelope))

      result.header.status shouldBe Status.OK
	    actualResponse shouldBe expectedResponse
    }
  }

	"Delete Envelope" should {
		"respond with 202 ACCEPTED status" in {
			val envelope = Support.envelope
			val request = FakeRequest("DELETE", s"/envelope/${envelope._id}")

      val controller = newController(deleteEnvelope = _ => Xor.right(envelope))
			val result = controller.delete(envelope._id)(request).futureValue

			status(result) shouldBe Status.ACCEPTED
		}

		"respond with 404 NOT FOUND status" in {
			val id: String = UUID.randomUUID().toString
			val request = FakeRequest("DELETE", s"/envelope/$id")

      val controller = newController(deleteEnvelope = _ => Xor.left(DeleteEnvelopeNotFoundError(s"Envelope $id not found")))
			val result = controller.delete(id)(request).futureValue

			val actualRespone = Json.parse(consume(result.body))
      val expectedResponse = Json.parse(s"""{"error" : {"msg": "Envelope $id not found" }}""")

			result.header.status shouldBe Status.NOT_FOUND
			actualRespone shouldBe expectedResponse
		}

		"respond with 500 INTERNAL SERVER ERROR status" in {
			val id: String = UUID.randomUUID().toString
			val request = FakeRequest("DELETE", s"/envelope/$id")

      val controller = newController(deleteEnvelope = _ => Future.failed(new Exception()))
      val result = controller.delete(id)(request).futureValue

			val actualResponse = Json.parse(consume(result.body))
			val expectedResponse = Json.parse("""{"error" : {"msg": "Internal Server Error" }}""")

			result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
			actualResponse shouldBe expectedResponse
		}
	}

}