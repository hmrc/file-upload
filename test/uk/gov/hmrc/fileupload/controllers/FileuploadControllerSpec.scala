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

//package uk.gov.hmrc.fileupload.controllers
//
//import akka.testkit.TestActorRef
//import play.api.http.Status
//import play.api.mvc._
//import play.api.test.{FakeHeaders, FakeRequest}
//import uk.gov.hmrc.fileupload.actors.ActorStub
//import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}
//
//class FileuploadControllerSpec extends UnitSpec with WithFakeApplication {
//
//	"once a file is uploaded the controller" should {
//		"ask the envelope service to add the file in the envelope" in {
//			val envelopeService =  Actors.envelopeService
//			val controller = FileController
//
//			envelopeService.asInstanceOf[TestActorRef[ActorStub]].underlyingActor.setReply(true)
//			val fakeRequest = new FakeRequest[String]("PUT", "/envelope",  FakeHeaders(), body =  "what ever")
//
//			val result: Result = await( controller.upload(envelopeId = "123", fileId = "456")(fakeRequest) )
//			result.header.status shouldBe Status.OK
//		}
//	}
//
//	"the controller" should {
//		"respond with 404 if the specified envelope does not exist" in {
//			val envelopeService =  Actors.envelopeService
//			val controller = FileController
//			val msg = new EnvelopeNotFoundException("123")
//
//			envelopeService.asInstanceOf[TestActorRef[ActorStub]].underlyingActor.setReply(msg)
//			val fakeRequest = new FakeRequest[String]("PUT", "/envelope",  FakeHeaders(), body =  "what ever")
//
//			val result: Result = await( controller.upload(envelopeId = "123", fileId = "456")(fakeRequest) )
//			result.header.status shouldBe Status.NOT_FOUND
//		}
//"respond with OK when a file is found" in {
//  val envelopeId = "myEnvId"
//  val fileId = "myFileId"
//  val request = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)
//  val fileEnumerator = mock[Enumerator[Array[Byte]]]
//
//  val fileFound: RetrieveFileResult = Xor.Right( FileFoundResult( Some("myfile.txt"), 100, fileEnumerator) )
//  val controller = new FileController(getMetadata= null, updateMetadata = null, retrieveFile = (_, _) => Future.successful(fileFound) )
//
//  val result: Result = controller.download(envelopeId, fileId)(request).futureValue
//
//  result.header.status shouldBe Status.OK
//  val headers = result.header.headers
//  headers("Content-Length") shouldBe "100"
//  headers("Content-Type") shouldBe "application/octet-stream"
//  headers("Content-Disposition") shouldBe "attachment; filename=\"myfile.txt\""
//}
//
//"respond with NOT FOUND when a file is not found" in {
//  val envelopeId = "myEnvId"
//  val fileId = "myFileId"
//  val request = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)
//  val fileEnumerator = mock[Enumerator[Array[Byte]]]
//
//  val fileFound: RetrieveFileResult = Xor.Left( FileNotFoundError )
//  val controller = new FileController(getMetadata= null, updateMetadata = null, retrieveFile = (_, _) => Future.successful(fileFound) )
//
//  val result: Result = controller.download(envelopeId, fileId)(request).futureValue
//
//  result.header.status shouldBe Status.NOT_FOUND
//	}
//}
