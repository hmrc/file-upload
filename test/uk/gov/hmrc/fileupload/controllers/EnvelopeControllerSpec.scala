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

package uk.gov.hmrc.fileupload.controllers

import org.apache.pekko.stream.scaladsl.Source
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.Inside
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.{HeaderNames, Status}
import play.api.libs.json.Json
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindError, FindMetadataError}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, File, FileStatusQuarantined}
import uk.gov.hmrc.fileupload.read.stats.Stats._
import uk.gov.hmrc.fileupload.write.envelope.{CreateEnvelope, EnvelopeCommand, EnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future, Promise}

class EnvelopeControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with TestApplicationComponents
     with ScalaFutures
     with IntegrationPatience {

  import Support._

  implicit val ec: ExecutionContext = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def newController(
    nextId               : () => EnvelopeId = () => EnvelopeId("abc-def"),
    handleCommand        : (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = _ => failed,
    findEnvelope         : EnvelopeId => Future[Either[FindError, Envelope]] = _ => failed,
    findMetadata         : (EnvelopeId, FileId) => Future[Either[FindMetadataError, read.envelope.File]] = (_, _) => failed,
    findAllInProgressFile: () => Future[GetInProgressFileResult] = () => failed,
    deleteInProgressFile : FileRefId => Future[Boolean] = _ => failed,
    getEnvelopesByStatus : (List[EnvelopeStatus], Boolean) => Source[Envelope, org.apache.pekko.NotUsed] = (_, _) => Source.failed(new Exception("not good"))
  ) = {
    val appModule = mock[ApplicationModule]
    when(appModule.nextId).thenReturn(nextId)
    when(appModule.envelopeCommandHandler).thenReturn(handleCommand)
    when(appModule.findEnvelope).thenReturn(findEnvelope)
    when(appModule.findMetadata).thenReturn(findMetadata)
    when(appModule.allInProgressFile).thenReturn(findAllInProgressFile)
    when(appModule.deleteInProgressFile).thenReturn(deleteInProgressFile)
    when(appModule.getEnvelopesByStatus).thenReturn(getEnvelopesByStatus)
    when(appModule.envelopeConstraintsConfigure).thenReturn(envelopeConstraintsConfigure)
    new EnvelopeController(appModule, app.injector.instanceOf[ControllerComponents])
  }

  "Create envelope with a request" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$host${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("abc-def")).url}"
    }
  }

  "Create envelope with a request with expiryDate == maxLimit" should {
    "return response with OK status" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(None,
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
    }
  }

  "Create envelope with unsupported callback url protocol" should {
    "return response with OK status" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("ftp://localhost/123"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with malformed callback url protocol" should {
    "return response with BAD_REQUEST status" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("%$#@%#$%#$%"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with valid callback url protocol" should  {
    "return response with OK status" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(Some("https://localhost:123"),
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.toHours.toInt))))

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
    }
  }



  "Create envelope with a request with too long expiryDate" should {
    "return response with BAD_REQUEST status" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest(None,
        Some(DateTime.now().plusHours(envelopeConstraintsConfigure.maxExpirationDuration.plusHours(1).toHours.toInt))))

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }
  }

  "Create envelope with no body" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.create()(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$host${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("abc-def")).url}"
    }
  }

  "Create envelope with id" should {
    "return response with OK status and a Location header specifying the envelope endpoint" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result: Result = controller.createWithId(EnvelopeId("aaa-bbb"))(fakeRequest).futureValue

      result.header.status shouldBe Status.CREATED
      val location = result.header.headers("Location")
      location shouldBe s"$host${uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(EnvelopeId("aaa-bbb")).url}"
    }
  }

  "Create envelope" should {
    "default to allowing zero length files" in {
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(), body = CreateEnvelopeRequest())

      val eventPromise = Promise[EnvelopeCommand]()

      val controller = newController(handleCommand = command => {
        eventPromise.success(command)
        Future.successful(Right(CommandAccepted))
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
      val host = "production.com:8000"

      val fakeRequest = FakeRequest("POST", s"http://$host/envelopes", FakeHeaders(),
        body = CreateEnvelopeRequest(constraints =
          Some(EnvelopeConstraintsUserSetting(allowZeroLengthFiles = Some(false)))))

      val eventPromise = Promise[EnvelopeCommand]()

      val controller = newController(handleCommand = command => {
        eventPromise.success(command)
        Future.successful(Right(CommandAccepted))
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

      val controller = newController(handleCommand = _ => Future.successful(Right(CommandAccepted)))
      val result = controller.delete(envelope._id)(request).futureValue

      result.header.status shouldBe Status.OK
    }

    "respond with 404 NOT FOUND status" in {
      val id = EnvelopeId()
      val request = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))

      val controller = newController(handleCommand = _ => Future.successful(Left(EnvelopeNotFoundError)))
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

      val controller = newController(findEnvelope = _ => Future.successful(Right(envelope)))
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
      val file = File(FileId(), fileRefId(), FileStatusQuarantined)
      val request = FakeRequest()

      val controller = newController(findMetadata = (_, _) => Future.successful(Right(file)))
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
