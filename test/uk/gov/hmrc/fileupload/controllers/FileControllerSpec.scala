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

///*
// * Copyright 2016 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.fileupload.controllers
//
//import cats.data.Xor
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.time.{Millis, Seconds, Span}
//import play.api.http.Status
//import play.api.libs.iteratee.Enumerator
//import play.api.libs.json.{JsObject, JsString, JsValue}
//import play.api.mvc._
//import play.api.test.{FakeHeaders, FakeRequest}
//import reactivemongo.json.JSONSerializationPack
//import reactivemongo.json.JSONSerializationPack.Document
//import uk.gov.hmrc.fileupload._
//import uk.gov.hmrc.fileupload.envelope.Service._
//import uk.gov.hmrc.fileupload.envelope.{Envelope, File, WithValidEnvelope}
//import uk.gov.hmrc.fileupload.read.file.Service._
//import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
//import play.api.test.Helpers._
//
//import scala.concurrent.{ExecutionContext, Future}
//
//class FileControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {
//
//  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))
//
//  implicit val ec = ExecutionContext.global
//
//  val failed = Future.failed(new Exception("not good"))
//
//  case class TestJsonReadFile(id: JsValue = JsString("testid")) extends JSONReadFile {
//    val pack = JSONSerializationPack
//    val contentType: Option[String] = None
//    val filename: Option[String] = None
//    val chunkSize: Int = 0
//    val length: Long = 0
//    val uploadDate: Option[Long] = None
//    val md5: Option[String] = None
//    val metadata: Document = null
//  }
//
//  def parse = UploadParser.parse(null) _
//
//  def newController(uploadBodyParser: (EnvelopeId, FileId) => BodyParser[Future[JSONReadFile]] = parse,
//                    retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_,_) => failed,
//                    withValidEnvelope: WithValidEnvelope = Support.envelopeAvailable(),
//                    uploadFile: UploadedFileInfo => Future[UpsertFileToEnvelopeResult] = _ => failed,
//                    upsertFileMetadata: UploadedFileMetadata => Future[UpdateMetadataResult] = _ => failed,
//                    deleteF: (EnvelopeId, FileId) => Future[DeleteFileResult] = (_, _) => failed) =
//    new FileController(uploadBodyParser = uploadBodyParser,
//      retrieveFile = retrieveFile,
//      withValidEnvelope = withValidEnvelope,
//      uploadFile = uploadFile,
//      upsertFileMetadata = upsertFileMetadata,
//      deleteFileFromEnvelope = deleteF)
//
//  "Upload a file" should {
//    "return 200 after the file is added to the envelope" in {
//      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))
//
//      val envelope = Support.envelope
//
//      val controller = newController(uploadFile = _ => Future.successful(Xor.right(UpsertFileSuccess)))
//      val result = controller.upsertFile(envelope._id, FileId())(fakeRequest).futureValue
//
//      result.header.status shouldBe Status.OK
//    }
//
//    "return 404 if envelope does not exist" in {
//      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))
//
//      val envelopeId = EnvelopeId()
//
//      val controller = newController(
//        withValidEnvelope = Support.envelopeNotFound
//      )
//      val result: Result = controller.upsertFile(envelopeId, FileId())(fakeRequest).futureValue
//
//      result.header.status shouldBe Status.NOT_FOUND
//    }
//  }
//
//  "Download a file" should {
//    "return 200 response with correct headers when file is found" in {
//      val fileFound = Xor.Right(FileFound(Some("myfile.txt"), 100, Enumerator.eof[ByteStream]))
//      val controller = newController(retrieveFile = (_,_) => Future.successful(fileFound))
//
//      val result = controller.downloadFile(EnvelopeId(), FileId())(FakeRequest()).futureValue
//
//      result.header.status shouldBe Status.OK
//      val headers = result.header.headers
//      headers("Content-Length") shouldBe "100"
//      headers("Content-Type") shouldBe "application/octet-stream"
//      headers("Content-Disposition") shouldBe "attachment; filename=\"myfile.txt\""
//    }
//    "return filename = `data` in headers if absent in client metadata for a given file" in {
//      val fileFound = Xor.Right(FileFound(None, 100, Enumerator.eof[ByteStream]))
//      val controller = newController(retrieveFile = (_,_) => Future.successful(fileFound))
//      val result = controller.downloadFile(EnvelopeId(), FileId())(FakeRequest()).futureValue
//
//      val headers = result.header.headers
//      headers("Content-Disposition") shouldBe "attachment; filename=\"data\""
//    }
//
//    "respond with 404 when a file is not found" in {
//      val envelopeId = EnvelopeId()
//      val fileId = FileId("myFileId")
//      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)
//
//      val fileFound: GetFileResult = Xor.Left(GetFileNotFoundError)
//      val controller = newController(
//        retrieveFile = (_,_) => Future.successful(fileFound)
//      )
//
//      val result: Result = controller.downloadFile(envelopeId, fileId)(fakeRequest).futureValue
//
//      result.header.status shouldBe Status.NOT_FOUND
//    }
//
//    "respond with 404 when envelope is not found" in {
//      val envelopeId = EnvelopeId()
//      val fileId = FileId()
//      val controller = newController(
//        withValidEnvelope = Support.envelopeNotFound
//      )
//
//      val result: Result = controller.downloadFile(envelopeId, fileId)(FakeRequest()).futureValue
//
//      result.header.status shouldBe Status.NOT_FOUND
//    }
//  }
//
//  "Retrieve metadata" should {
//    "be successful if metadata exists" in {
//      val fileId = FileId()
//      val file = File(fileId = fileId)
//      val envelope = Support.envelope.copy(files = Some(List(file)))
//      val envelopeAvailable = Support.envelopeAvailable(envelope)
//      val controller = newController(
//        withValidEnvelope = envelopeAvailable
//      )
//
//      val result = controller.retrieveMetadata(envelope._id, fileId)(FakeRequest()).futureValue
//
//      result.header.status shouldBe 200
//    }
//
//    "fail if metadata does not exist" in {
//      val fileId = FileId("nonexistent")
//      val envelope = Support.envelope
//      val controller = newController()
//
//      val result = controller.retrieveMetadata(envelope._id, fileId)(FakeRequest()).futureValue
//
//      status(result) shouldBe 404
//      contentAsString(result) should include(s"File with id: $fileId not found in envelope: ${envelope._id}")
//    }
//
//    "fail if envelope does not exist" in {
//      val controller = newController(
//        withValidEnvelope = Support.envelopeNotFound
//      )
//      val nonexistentEnvelopeId = EnvelopeId("nonexistent")
//
//      val result = controller.retrieveMetadata(nonexistentEnvelopeId, FileId())(FakeRequest()).futureValue
//
//      status(result) shouldBe 404
//      contentAsString(result) should include(s"Envelope with id: $nonexistentEnvelopeId not found")
//    }
//  }
//
//  "Delete file" should {
//    "be successful" in {
//      val fileId = FileId()
//      val envelopeId = EnvelopeId()
//      val deleteFile: (EnvelopeId, FileId) => Future[DeleteFileResult] = (_, _) => Future.successful(Xor.right(fileId))
//      val controller = newController(
//        deleteF = deleteFile
//      )
//
//      val result = controller.deleteFile(envelopeId, fileId)(FakeRequest()).futureValue
//
//      result.header.status shouldBe 200
//    }
//
//    "fail if file does not exist" in {
//      val fileId = FileId()
//      val envelopeId = EnvelopeId()
//      val deleteFile: (EnvelopeId, FileId) => Future[DeleteFileResult] = (_, _) => Future.successful(Xor.left(DeleteFileNotFoundError))
//      val controller = newController(
//        deleteF = deleteFile
//      )
//
//      val result = controller.deleteFile(envelopeId, fileId)(FakeRequest()).futureValue
//
//      result.header.status shouldBe NOT_FOUND
//    }
//
//    "respond with 500 INTERNAL SERVER ERROR status" in {
//      val fileId = FileId()
//      val envelopeId = EnvelopeId()
//      val deleteFile: (EnvelopeId, FileId) => Future[DeleteFileResult] = (_, _) => Future.successful(Xor.left(DeleteFileServiceError("error")))
//      val controller = newController(
//        deleteF = deleteFile
//      )
//
//      val result = controller.deleteFile(envelopeId, fileId)(FakeRequest()).futureValue
//
//      result.header.status shouldBe INTERNAL_SERVER_ERROR
//    }
//  }
//}
