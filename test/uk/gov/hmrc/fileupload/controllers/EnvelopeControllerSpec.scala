/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.scaladsl.Source
import cats.data.Xor
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.{AlwaysAuthorisedBasicAuth, BasicAuth}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindError, FindMetadataError}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, File, FileStatusQuarantined}
import uk.gov.hmrc.fileupload.read.stats.Stats._
import uk.gov.hmrc.fileupload.write.envelope.{CreateEnvelope, EnvelopeCommand, EnvelopeCreated, EnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future, Promise}

class EnvelopeControllerSpec extends UnitSpec with ApplicationComponents with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  import Support._

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def newController(withBasicAuth: BasicAuth = AlwaysAuthorisedBasicAuth,
                    nextId: () => EnvelopeId = () => EnvelopeId("abc-def"),
                    handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]] = _ => failed,
                    findEnvelope: EnvelopeId => Future[Xor[FindError, Envelope]] = _ => failed,
                    findMetadata: (EnvelopeId, FileId) => Future[Xor[FindMetadataError, read.envelope.File]] = (_, _) => failed,
                    findAllInProgressFile: () => Future[GetInProgressFileResult] = () => failed,
                    deleteInProgressFile: FileRefId => Future[Boolean] = _ => failed,
                    getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed] = (_, _) => failed) =
    new EnvelopeController(withBasicAuth, nextId, handleCommand, findEnvelope, findMetadata,
                           findAllInProgressFile, deleteInProgressFile, getEnvelopesByStatus, envelopeConstraintsConfigure)

  "Create envelope with a request" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
	    val serverUrl = "http://production.com:8000"

	    val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())/*TODO {
		    override lazy val host = serverUrl
	    }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
	    val location = result.header.headers("Location")
	    location shouldBe s"$serverUrl${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("abc-def")).url}"
    }
  }

  "Create envelope with a request with expiryDate == maxLimit" should {
    "return response with OK status" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(None,
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
    }
  }

  "Create envelope with unsupported callback url protocol" should {
    "return response with OK status" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("ftp://localhost/123"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with malformed callback url protocol" should {
    "return response with BAD_REQUEST status" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("%$#@%#$%#$%"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with valid callback url protocol" should  {
    "return response with OK status" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("https://localhost:123"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
    }
  }



  "Create envelope with a request with too long expiryDate" should {
    "return response with BAD_REQUEST status" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(None,
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.plusHours(1).toHours.toInt))))/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with no body" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())/* {
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$serverUrl${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("abc-def")).url}"
    }
  }

  "Create envelope with id" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())/*{
        override lazy val host = serverUrl
      }*/

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result: Result = controller.createWithId(EnvelopeId("aaa-bbb"))(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$serverUrl${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("aaa-bbb")).url}"
    }
  }

  "Create envelope" should {
    "default to allowing zero length files" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())/*{
        override lazy val host = serverUrl
      }*/

      val eventPromise = Promise[EnvelopeCommand]

      val controller = newController(handleCommand = command => {
        eventPromise.success(command)
        Future.successful(Xor.right(CommandAccepted))
      })
      val result: Result = controller.createWithId(EnvelopeId("aaa-bbb"))(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED

      val envelopeCommand = eventPromise.future.futureValue

      Inside.inside(envelopeCommand) {
        case ce: CreateEnvelope =>
          ce.constraints.flatMap(_.allowZeroLengthFiles) shouldBe Some(true)
      }
    }
  }

  "Create envelope" should {
    "can be overriden to disallow zero length files" in {
      val serverUrl = "http://production.com:8000"

      val fakeRequest = FakeRequest("POST", "/envelopes", FakeHeaders(),
        body = CreateEnvelopeRequest(constraints =
          Some(EnvelopeConstraintsUserSetting(allowZeroLengthFiles = Some(false)))))/*{
        override lazy val host = serverUrl
      }*/

      val eventPromise = Promise[EnvelopeCommand]

      val controller = newController(handleCommand = command => {
        eventPromise.success(command)
        Future.successful(Xor.right(CommandAccepted))
      })
      val result: Result = controller.createWithId(EnvelopeId("aaa-bbb"))(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED

      val envelopeCommand = eventPromise.future.futureValue

      Inside.inside(envelopeCommand) {
        case ce: CreateEnvelope =>
          ce.constraints.flatMap(_.allowZeroLengthFiles) shouldBe Some(false)
      }
    }
  }

	"Delete Envelope" should {
		"respond with 200 OK status" in {
			val envelope = Support.envelope
			val request = FakeRequest("DELETE", s"/envelopes/${envelope._id}").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
			val result = controller.delete(envelope._id)(request).futureValue

			status(result) shouldBe Status.OK
		}

		"respond with 404 NOT FOUND status" in {
			val id = EnvelopeId()
			val request = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))

      val controller = newController(handleCommand = _ => Future.successful(Xor.left(EnvelopeNotFoundError)))
			val result = controller.delete(id)(request).futureValue

			val actualRespone = Json.parse(consume(result.body))
      val expectedResponse = Json.parse(s"""{"error" : {"msg": "Envelope with id: $id not found" }}""")

			result.header.status shouldBe Status.NOT_FOUND
			actualRespone shouldBe expectedResponse
		}

		"respond with 500 INTERNAL SERVER ERROR status" in {
			val id = EnvelopeId()
			val request = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))

      val controller = newController(handleCommand = _ => Future.failed(new Exception()))
      val result = controller.delete(id)(request).futureValue

			val actualResponse = Json.parse(consume(result.body))
			val expectedResponse = Json.parse("""{"error" : {"msg": "Internal Server Error" }}""")

			result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
			actualResponse shouldBe expectedResponse
		}
	}

  "Get Envelope" should {
    "return an  envelope resource when request id is valid" in {
      val envelope = Support.envelope
      val request = FakeRequest()

      val controller = newController(findEnvelope = _ => Xor.right(envelope))
      val result = controller.show(envelope._id)(request).futureValue

      val actualResponse = Json.parse(consume(result.body))

      import EnvelopeReport._
      val expectedResponse = Json.toJson(EnvelopeReport.fromEnvelope(envelope))

      result.header.status shouldBe Status.OK
      actualResponse shouldBe expectedResponse
    }
  }

  "Get Metadata" should {
    "return an  envelope resource when request id is valid" in {
      val envelopeId = EnvelopeId()
      val file = File(FileId(), FileRefId(), FileStatusQuarantined)
      val request = FakeRequest()

      val controller = newController(findMetadata = (_, _) => Xor.right(file))
      val result = controller.retrieveMetadata(envelopeId, file.fileId)(request).futureValue

      val actualResponse = Json.parse(consume(result.body))

      import GetFileMetadataReport._
      val expectedResponse = Json.toJson(fromFile(envelopeId, file))

      result.header.status shouldBe Status.OK
      actualResponse shouldBe expectedResponse
    }
  }

  "Delete File in progress with FileRefId" should {
    "respond with 200 OK status" in {
      val envelope = Support.envelopeWithAFile(FileId())

      val request = FakeRequest("DELETE", s"/file-upload/files/inprogress/fileRefId")

      val controller = newController(deleteInProgressFile = _ => Future.successful(true))
      val result = controller.deleteInProgressFileByRefId(envelope.files.get.head.fileRefId)(request).futureValue

      result.header.status shouldBe Status.OK
    }

    "respond with 500 status" in {
      val envelope = Support.envelopeWithAFile(FileId())

      val request = FakeRequest("DELETE", s"/file-upload/files/inprogress/fileRefId")

      val controller = newController(deleteInProgressFile = _ => Future.successful(false))
      val result = controller.deleteInProgressFileByRefId(envelope.files.get.head.fileRefId)(request).futureValue

      result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

}
