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

import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsJson, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.{ActorStub, FileUploadTestActors}
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Success, Try}

class EnvelopeControllerSpec  extends UnitSpec with WithFakeApplication {

  import uk.gov.hmrc.fileupload.Support._

  import scala.concurrent.duration._
  import FileUploadTestActors._
  import scala.language.postfixOps
  import Envelope._
	import Support.Implicits._

  implicit val ec = ExecutionContext.global
  override implicit val defaultTimeout = 500 milliseconds

  "create envelope with a request" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {

	    val serverUrl = "http://production.com:8000"

	    val fakeRequest = new FakeRequest[AnyContentAsJson]("POST", "/envelope",  FakeHeaders(), body =  AnyContentAsJson(envelopeBody)){
		    override lazy val host = serverUrl
	    }

			val id: String = UUID.randomUUID().toString
	    val envelopeMgr: ActorStub = FileUploadTestActors.envelopeService
			envelopeMgr.setReply(id)

      val result: Result = await( EnvelopeController.create()(fakeRequest) )
			result.header.status shouldBe Status.OK
	    val location = result.header.headers.get("Location").get
	    location shouldBe s"$serverUrl${routes.EnvelopeController.show(id).url}"


    }
  }

  "Get Envelope" should {
    "return an envelope resource when request id is valid" in {
			val id: String = UUID.randomUUID().toString
      val envelope = Support.envelope
      val request = FakeRequest("GET", s"/envelope/$id")
      val envelopeMgr: ActorStub = FileUploadTestActors.envelopeService

      envelopeMgr.setReply(Try(Json.toJson[Envelope](envelope)))


      val futureResult = EnvelopeController.show(id)(request)

      val result = Await.result(futureResult, defaultTimeout)

      val actualResponse = Json.parse(consume(result.body))
      val expectedResponse = Json.toJson(envelope)

      result.header.status shouldBe Status.OK
	    actualResponse shouldBe expectedResponse

    }
  }

	"Delete Envelope" should {
		"respond with 200 OK status" in {
			val id: String = UUID.randomUUID().toString
			val request = FakeRequest("DELETE", s"/envelope/${id}")

			val envelopeMgr: ActorStub = FileUploadTestActors.envelopeService
			envelopeMgr.setReply(true)

			val futureResult = EnvelopeController.delete(id)(request)
			status(futureResult) shouldBe Status.OK

		}

		"respond with 404 NOT FOUND status" in {
			val id: String = UUID.randomUUID().toString
			val request = FakeRequest("DELETE", s"/envelope/$id")

			val envelopeMgr: ActorStub = FileUploadTestActors.envelopeService
			envelopeMgr.setReply(false)

			val futureResult = EnvelopeController.delete(id)(request)
			status(futureResult) shouldBe Status.NOT_FOUND

		}

		"respond with 500 INTERNAL SERVER ERROR status" in {
			val id: String = UUID.randomUUID().toString
			val request = FakeRequest("DELETE", s"/envelope/$id")

			val envelopeMgr: ActorStub = FileUploadTestActors.envelopeService
			envelopeMgr.setReply(new Exception("something broken"))

			val futureResult = EnvelopeController.delete(id)(request)
			val result = Await.result(futureResult, defaultTimeout)

			val actualRespone = Json.parse(consume(result.body))
			val expectedResponse = Json.parse("""{"reason": "Internal Server Error" }""")

			result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
			actualRespone shouldBe expectedResponse
		}

	}

}