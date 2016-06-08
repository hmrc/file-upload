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

import akka.testkit.TestActorRef
import org.joda.time.DateTime
import play.api.Play
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsJson, AnyContent, AnyContentAsEmpty, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.{ActorStub, EnvelopeService$, FileUploadTestActors, Actors}
import uk.gov.hmrc.fileupload.models.{Envelope, Constraints}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Await, ExecutionContext, Future}

class EnvelopeControllerSpec  extends UnitSpec with WithFakeApplication {

  import uk.gov.hmrc.fileupload.Support._

  import scala.concurrent.duration._
  import FileUploadTestActors._
  import scala.language.postfixOps
  import Envelope._

  implicit val ec = ExecutionContext.global
  override implicit val defaultTimeout = 500 milliseconds

  "create envelope with a request" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {

	    val serverUrl = "http://production.com:8000"

	    val fakeRequest = new FakeRequest[AnyContentAsJson]("POST", "/envelope",  FakeHeaders(), body =  AnyContentAsJson(envelopeBody)){
		    override lazy val host = serverUrl
	    }

			val id: BSONObjectID = BSONObjectID.generate
	    val envelopeMgr: ActorStub = FileUploadTestActors.envelopeMgr
			envelopeMgr.setReply(id)

      val result: Result = await( EnvelopeController.create()(fakeRequest) )
			result.header.status shouldBe Status.OK
	    val location = result.header.headers.get("Location").get
	    location shouldBe s"$serverUrl${routes.EnvelopeController.show(id.stringify).url}"


    }
  }

  "Get Envelope" should {
    "return an envelope resource when request id is valid" in {
      val id = BSONObjectID.generate
      val envelope = Support.envelope
      val request = FakeRequest("GET", s"/envelope/${id.toString}")
      val envelopeMgr: ActorStub = FileUploadTestActors.envelopeMgr

      envelopeMgr.setReply(Some(envelope))


      val futureResult = EnvelopeController.show(id.toString)(request)

      val result = Await.result(futureResult, defaultTimeout)

      val actualRespone = Json.parse(consume(result.body))
      val expectedResponse = Json.toJson(envelope)

      result.header.status shouldBe Status.OK
      actualRespone shouldBe expectedResponse

    }
  }



}