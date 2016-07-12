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

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.test.{FakeHeaders, FakeRequest}
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack.Document
import uk.gov.hmrc.fileupload.{ByteStream, JSONReadFile, Support}
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.file.Repository._
import uk.gov.hmrc.fileupload.file.{CompositeFileId, FileMetadata}
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class FileControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  case class TestJsonReadFile(id: JsValue = null) extends JSONReadFile {
    val pack = JSONSerializationPack
    val contentType: Option[String] = None
    val filename: Option[String] = None
    val chunkSize: Int = 0
    val length: Long = 0
    val uploadDate: Option[Long] = None
    val md5: Option[String] = None
    val metadata: Document = null
  }

  def parse = UploadParser.parse(null) _

  def newController(uploadBodyParser: CompositeFileId => BodyParser[Future[JSONReadFile]] = parse,
                    addFileToEnvelope: (String, String) => Future[AddFileResult] = (_, _) => failed,
                    getMetadata: CompositeFileId => Future[GetMetadataResult] = _ => failed,
                    updateMetadata: FileMetadata => Future[UpdateMetadataResult] = _ => failed,
                    retrieveFile: CompositeFileId => Future[RetrieveFileResult] = _ => failed) =
    new FileController(uploadBodyParser = uploadBodyParser,
      addFileToEnvelope = addFileToEnvelope,
      getMetadata = getMetadata,
      updateMetadata = updateMetadata,
      retrieveFile = retrieveFile)

  "Upload a file" should {
    "return 200 after the file is added to the envelope" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(addFileToEnvelope = (_, _) => Future.successful(Xor.right(envelope)))
      val result: Result = controller.upload(envelope._id, "456")(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
    }

    "return 400 if envelope is sealed" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(addFileToEnvelope = (_, _) => Future.successful(Xor.left(AddFileSeaeldError(envelope))))
      val result: Result = controller.upload(envelope._id, "456")(fakeRequest).futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return 404 if envelope does not exist" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(addFileToEnvelope = (_, _) => Future.successful(Xor.left(AddFileEnvelopeNotFoundError(envelope._id))))
      val result: Result = controller.upload(envelope._id, "456")(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }

  "Download a file" should {
    "return 200 when file is found" in {
      val envelopeId = "myEnvId"
      val fileId = "myFileId"
      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)

      val fileFound: RetrieveFileResult = Xor.Right(FileFoundResult(Some("myfile.txt"), 100, Enumerator.eof[ByteStream]))

      val controller = newController(retrieveFile = _ => Future.successful(fileFound))
      val result = controller.download(envelopeId, fileId)(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
      val headers = result.header.headers
      headers("Content-Length") shouldBe "100"
      headers("Content-Type") shouldBe "application/octet-stream"
      headers("Content-Disposition") shouldBe "attachment; filename=\"myfile.txt\""
    }

    "respond with 404 when a file is not found" in {
      val envelopeId = "myEnvId"
      val fileId = "myFileId"
      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)

      val fileFound: RetrieveFileResult = Xor.Left(FileNotFoundError)
      val controller = newController(retrieveFile = _ => Future.successful(fileFound))

      val result: Result = controller.download(envelopeId, fileId)(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }
}
