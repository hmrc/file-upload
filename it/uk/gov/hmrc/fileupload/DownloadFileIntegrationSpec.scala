package uk.gov.hmrc.fileupload

import java.io.RandomAccessFile

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}


/**
  * Integration tests for FILE-104
  * Download File
  *
  */
class DownloadFileIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions with FakeFrontendService {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(45, Seconds), interval = Span(500, Millis))

  val data = "{'name':'pete'}"
  feature("Download File") {

    scenario("Check that a file can be downloaded") {

      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String, manual test passed

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data.getBytes)))

      When(s"I invoke GET envelope/$envelopeId/files/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK

      And("I will receive the file")
      response.body shouldBe data

      And("Header should include content length")
      response.header("Content-Length") shouldBe Some(s"${data.getBytes.length}")

      And("Header should include content disposition")
      response.header("Content-Disposition") shouldBe Some("attachment; filename=\"test.pdf\"")
    }

    scenario("File can not be found") {

      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have an invalid file id")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String

      When(s"I invoke GET envelope/$envelopeId/files/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    scenario("Valid file can be downloaded") {

      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()

      And("I have a valid file ID")
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val newFileName = "new-file-name.pdf"
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, newFileName, "pdf", Some(123L), Json.obj()))

      And("a file has previously been uploaded to the transient store after it's marked clean")
      val file = new RandomAccessFile("t", "rw")
      file.setLength(1024 * 1024 * 2)
      val data = new Array[Byte](file.length().toInt)
      file.readFully(data)
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.length))

      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.reset()
      md.update(data)
      val sourceDigest = md.digest()

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data)))

      When("I call GET /file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK

      And("the file is downloaded successfully")
      val storedFile: Array[Byte] = getFileResponse.body.getBytes
      md.reset()
      md.update(storedFile)
      val storedDigest = md.digest()

      And("the filename within the metadata has been applied")
      getFileResponse.header("Content-Disposition") shouldBe Some(s"""attachment; filename=\"$newFileName"""")

      And("the downloaded file is identical to the original file")
      sourceDigest shouldEqual storedDigest
    }
  }
}
