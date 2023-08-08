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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.mockito.scalatest.MockitoSugar
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.http.{HeaderNames, Status}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.read.envelope.{File, FileStatusAvailable, WithValidEnvelope}
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeCommand
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}

import scala.concurrent.{ExecutionContext, Future}

class FileControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with TestApplicationComponents
     with MockitoSugar
     with ScalaFutures
     with OptionValues
     with IntegrationPatience {

  import uk.gov.hmrc.fileupload.Support.StreamImplicits.system
  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def newController(
    retrieveFile     : (EnvelopeId, FileId) => Future[Source[ByteString, _]] = (_, _) => failed,
    withValidEnvelope: WithValidEnvelope = new WithValidEnvelope(_ => Future.successful(Some(Support.envelope))),
    handleCommand    : EnvelopeCommand => Future[Either[CommandNotAccepted, CommandAccepted.type]] = _ => failed
  ) = {
    val appModule = mock[ApplicationModule]
    when(appModule.getFileFromS3).thenReturn(retrieveFile)
    when(appModule.withValidEnvelope).thenReturn(withValidEnvelope)
    when(appModule.envelopeCommandHandler).thenReturn(handleCommand)
    new FileController(appModule, app.injector.instanceOf[ControllerComponents])
  }


  val envelopeId = EnvelopeId()
  val fileId = FileId()
  val refId = FileRefId("someTextNotUuidFormat")
  val file = File(
    fileId,
    refId,
    FileStatusAvailable,
    name   = Some(FileName("myfile.txt")),
    length = Some(100)
  )
  val envelope = Support.envelope.copy(files = Some(Seq(file))).copy(_id = envelopeId)
  val source = Source.empty[ByteString]
  val authHeaders = HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword"))

  "Download a file" should {
    "return 200 response with correct headers when file is found" in {
      val controller =
        newController(
          retrieveFile = (_, _) => Future.successful(source),
          withValidEnvelope = new WithValidEnvelope(
            _ => Future.successful(Some(envelope))
          ))

      val result = controller.downloadFile(envelopeId, fileId)(FakeRequest().withHeaders(authHeaders))

      status(result) shouldBe Status.OK
      result.futureValue.body.contentLength shouldBe Some(100)
      contentType(result) shouldBe  Some("application/octet-stream")
      header("Content-Disposition", result).value shouldBe "attachment; filename=\"myfile.txt\""
    }

    "return filename = `data` in headers if absent in client metadata for a given file" in {
      val controller =
        newController(
          retrieveFile = (_, _) => Future.successful(source),
          withValidEnvelope = new WithValidEnvelope(
            _ => {
              val fileWithoutAName = file.copy(name = None)
              val envelopeWithoutFileName = envelope.copy(files = Some(Seq(fileWithoutAName)))
              Future.successful(Some(envelopeWithoutFileName))
            }))

      val result = controller.downloadFile(envelopeId, fileId)(FakeRequest().withHeaders(authHeaders))

      header("Content-Disposition", result).value shouldBe "attachment; filename=\"data\""
    }

    "respond with 404 when a file is not found" in {
      val randomFileId = FileId("myFileId")
      val controller = newController(retrieveFile = (_,_) => Future.successful(source))

      val result = controller.downloadFile(envelopeId, randomFileId)(FakeRequest().withHeaders(authHeaders))

      status(result) shouldBe Status.NOT_FOUND
      contentAsString(result) should include (s"File with id: $randomFileId not found")
    }

    "respond with 404 when envelope is not found" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val controller = newController(
        withValidEnvelope = Support.envelopeNotFound
      )

      val result = controller.downloadFile(envelopeId, fileId)(FakeRequest().withHeaders(authHeaders))

      status(result) shouldBe Status.NOT_FOUND
      contentAsString(result) should include (s"Envelope with id: $envelopeId not found")
    }
  }
}
