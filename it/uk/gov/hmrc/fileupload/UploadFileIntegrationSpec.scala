package uk.gov.hmrc.fileupload

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

/**
  * Integration tests for FILE-83
  * Upload File
  *
  */
class UploadFileIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

  feature("Upload File") {

    info("I want to upload a file")
    info("So that I can persist it in the the database")

    scenario("Add file to envelope (valid)") {

      Given("I have a valid envelope-id")
      stubCallback()

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl())))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextUtf8String()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      When(s"StoreFile($envelopeId, $fileId, $fileRefId, 123L) command is sent")
      val response: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 123L))

      Then("I will receive a 200 OK response")
      eventually { verifyAvailableCallbackReceived(envelopeId = envelopeId, fileId = fileId) }

      response.status shouldBe OK
    }

    scenario("Add valid 3MB file to envelope") {
      val fileSize = (1024 * 1024 * 3).toLong
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextUtf8String()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have a valid 3MB file attached to the request body")

      When(s"StoreFile($envelopeId, $fileId, $fileRefId, 3MB) command is sent")
      val response:WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, fileSize))

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }

    scenario("Add multiple files to envelope") {
      Given("I have an envelope-id with an existing file attached")
      val envelopeId = createEnvelope()
      val firstFileId = FileId(s"fileId-${nextUtf8String()}")
      val firstFileRefId = FileRefId(s"fileRefId-${nextId()}")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, firstFileId, firstFileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      sendCommandStoreFile(StoreFile(envelopeId, firstFileId, firstFileRefId, "{}".getBytes.length))

      And("And I have a valid new file-id")
      val secondFileId = FileId(s"fileId-${nextUtf8String()}")
      val data = "{}".getBytes

      And("I have a valid file-ref-id")
      val secondFileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, secondFileId, secondFileRefId, 0, "test.pdf", "pdf", Some(data.length), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, secondFileId, secondFileRefId))

      When(s"StoreFile($envelopeId, $secondFileId, $secondFileRefId, data.length) command is sent")
      val response = sendCommandStoreFile(StoreFile(envelopeId, secondFileId, secondFileRefId, data.length))

      Then("I will receive a 200 OK response")
      response.status shouldBe OK

      And("there should be 2 files available within that envelope")
      val envelope = getEnvelopeFor(envelopeId)
      (envelope.json \ "files").as[List[JsObject]].size shouldBe 2
    }

    scenario("Add file with invalid envelope-id") {
      Given("I have a invalid envelope-id")
      val envelopeId = EnvelopeId("invalidId")

      And("I have a file id")
      val fileId = FileId(s"fileId-${nextUtf8String()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      When(s"StoreFile($envelopeId, $fileId, $fileRefId, 123KB) command is sent")
      val response: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 123L))

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    scenario("Add file with no file attached") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"nofile-${nextUtf8String()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have no file attached to the request body")
      val data = "".getBytes

      When(s"StoreFile($envelopeId, $fileId, $fileRefId, 0) command is sent")
      val response: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 0))

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
