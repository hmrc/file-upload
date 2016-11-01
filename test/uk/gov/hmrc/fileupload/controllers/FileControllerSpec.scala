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
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.{HeaderNames, Status}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsString, JsValue}
import play.api.mvc._
import play.api.test.{FakeHeaders, FakeRequest}
import reactivemongo.api.commands.WriteResult
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack.Document
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.{AlwaysAuthorisedBasicAuth, BasicAuth}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, WithValidEnvelope}
import uk.gov.hmrc.fileupload.read.file.Service._
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class FileControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  case class TestJsonReadFile(id: JsValue = JsString("testid")) extends JSONReadFile {
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

  def newController(withBasicAuth:BasicAuth = AlwaysAuthorisedBasicAuth,
                    uploadBodyParser: (EnvelopeId, FileId, FileRefId) => BodyParser[Future[JSONReadFile]] = parse,
                    retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_, _) => failed,
                    withValidEnvelope: WithValidEnvelope = Support.envelopeAvailable(),
                    handleCommand: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]] = _ => failed,
                    clear: () => Future[List[WriteResult]] = () => failed) =
    new FileController(
      withBasicAuth,
      uploadBodyParser = uploadBodyParser,
      retrieveFile = retrieveFile,
      withValidEnvelope = withValidEnvelope,
      handleCommand = handleCommand,
      clear = clear)

  "Upload a file" should {
    "return 200 after the file is added to the envelope" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelopes", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(handleCommand = _ => Future.successful(Xor.right(CommandAccepted)))
      val result = controller.upsertFile(envelope._id, FileId(), FileRefId())(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
    }

    "return 404 if envelope does not exist" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelopes", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelopeId = EnvelopeId()

      val controller = newController(handleCommand = _ => Future.successful(Xor.left(EnvelopeNotFoundError)))
      val result: Result = controller.upsertFile(envelopeId, FileId(), FileRefId())(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }

  "Download a file" should {
    "return 200 response with correct headers when file is found" in {
      val fileFound = Xor.Right(FileFound(Some("myfile.txt"), 100, Enumerator.eof[ByteStream]))
      val controller = newController(retrieveFile = (_,_) => Future.successful(fileFound))

      val result = controller.downloadFile(EnvelopeId(), FileId())(FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))).futureValue

      result.header.status shouldBe Status.OK
      val headers = result.header.headers
      headers("Content-Length") shouldBe "100"
      headers("Content-Type") shouldBe "application/octet-stream"
      headers("Content-Disposition") shouldBe "attachment; filename=\"myfile.txt\""
    }
    "return filename = `data` in headers if absent in client metadata for a given file" in {
      val fileFound = Xor.Right(FileFound(None, 100, Enumerator.eof[ByteStream]))
      val controller = newController(retrieveFile = (_,_) => Future.successful(fileFound))
      val result = controller.downloadFile(EnvelopeId(), FileId())(FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))).futureValue

      val headers = result.header.headers
      headers("Content-Disposition") shouldBe "attachment; filename=\"data\""
    }

    "respond with 404 when a file is not found" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId("myFileId")
      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelopes/$envelopeId/files/$fileId/content", FakeHeaders(), body = null).withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))

      val fileFound: GetFileResult = Xor.Left(GetFileNotFoundError)
      val controller = newController(
        retrieveFile = (_,_) => Future.successful(fileFound)
      )

      val result: Result = controller.downloadFile(envelopeId, fileId)(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }

    "respond with 404 when envelope is not found" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val controller = newController(
        withValidEnvelope = Support.envelopeNotFound
      )

      val result: Result = controller.downloadFile(envelopeId, fileId)(FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }
}
