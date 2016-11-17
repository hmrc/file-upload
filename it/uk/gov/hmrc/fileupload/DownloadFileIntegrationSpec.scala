package uk.gov.hmrc.fileupload

import java.io.RandomAccessFile

import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.controllers.adminController.FileInQuarantineStored
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}


/**
  * Integration tests for FILE-104
  * Download File
  *
  */
class DownloadFileIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  feature("Download File") {

    scenario("Check that a file can be downloaded") {
      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))

      And("I have uploaded a file")
      val data = "{'name':'pete'}"
      upload(data.getBytes, envelopeId, fileId, fileRefId)

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
      val fileId = FileId(s"fileId-${nextId()}")

      When(s"I invoke GET envelope/$envelopeId/files/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }
    
    scenario("Valid file can be downloaded") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()

      And("I have a valid file ID")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val newFileName = "new-file-name.pdf"
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, newFileName, "pdf", Json.obj()))

      And("a file has previously been uploaded to the transient store")
      val file = new RandomAccessFile("t", "rw")
      file.setLength(1024 * 1024 * 2)
      val data = new Array[Byte](file.length().toInt)
      file.readFully(data)
      val putFileResponse: WSResponse = upload(data, envelopeId, fileId, fileRefId)
      val md = java.security.MessageDigest.getInstance("SHA-256")
      md.reset()
      md.update(data)
      val sourceDigest = md.digest()

      When("I call GET /file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse : WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK

      And("the file is downloaded successfully")
      val storedFile : Array[Byte] = getFileResponse.body.getBytes
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
