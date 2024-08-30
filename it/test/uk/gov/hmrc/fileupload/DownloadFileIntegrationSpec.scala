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

package uk.gov.hmrc.fileupload

import java.io.RandomAccessFile

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}


/**
  * Integration tests for FILE-104
  * Download File
  *
  */
class DownloadFileIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FileActions
     with EventsActions
     with FakeFrontendService
     with OptionValues
     with IntegrationPatience {

  val data = "{'name':'pete'}"

  Feature("Download File") {

    Scenario("Check that a file can be downloaded") {
      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String, manual test passed

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val filename = FileName("test–.pdf") // contains a non-ascii char which needs escaping
      sendCommandQuarantineFile(QuarantineFile(
        envelopeId,
        fileId,
        fileRefId,
        0,
        filename,
        "pdf",
        Some(123L),
        Json.obj()
      ))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/internal-file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data.getBytes)))

      Thread.sleep(1000)

      When(s"I invoke GET envelope/$envelopeId/files/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK

      And("I will receive the file")
      response.body[String] shouldBe data

      And("Header should include content length")
      response.header("Content-Length") shouldBe Some(s"${data.getBytes.length}")

      And("Header should include content disposition")
      response.header("Content-Disposition").value should startWith ("attachment; filename=\"test?.pdf\"")
    }

    Scenario("File can not be found") {
      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have an invalid file id")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String

      When(s"I invoke GET envelope/$envelopeId/files/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    Scenario("Valid file can be downloaded") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()

      And("I have a valid file ID")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val newFileName = FileName("new-file-name.pdf")
      sendCommandQuarantineFile(QuarantineFile(
        envelopeId,
        fileId,
        fileRefId,
        0,
        newFileName,
        "pdf",
        Some(123L),
        Json.obj()
      ))

      And("a file has previously been uploaded to the transient store after it's marked clean")
      val file = RandomAccessFile("t", "rw")
      file.setLength(1024 * 1024 * 2)
      val data = new Array[Byte](file.length().toInt)
      file.readFully(data)
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.length))

      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.reset()
      md.update(data)
      val sourceDigest = md.digest()

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/internal-file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data)))

      Thread.sleep(1000)

      When("I call GET /internal-file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK

      And("the file is downloaded successfully")
      val storedFile: Array[Byte] = getFileResponse.body.getBytes
      md.reset()
      md.update(storedFile)
      val storedDigest = md.digest()

      And("the filename within the metadata has been applied")
      getFileResponse.header("Content-Disposition") shouldBe Some(s"""attachment; filename=\"${newFileName.value}"""")

      And("the downloaded file is identical to the original file")
      sourceDigest shouldEqual storedDigest
    }
  }
}
