package uk.gov.hmrc.fileupload

import java.io.RandomAccessFile

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}
import uk.gov.hmrc.fileupload.support._

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
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      And("I have a valid file attached to the request body")
      val data = "{}".getBytes

      When(s"I invoke PUT envelope/$envelopeId/files/$fileId/$fileRefId")
      val response: WSResponse = upload(data, envelopeId, fileId, fileRefId)

      Then("I will receive a 200 OK response")
      eventually { verifyAvailableCallbackReceived(envelopeId = envelopeId, fileId = fileId) }

      response.status shouldBe OK
    }

    scenario("Add valid 3MB file to envelope") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      And("I have a valid 3MB file attached to the request body")
      val file = new RandomAccessFile("t", "rw")
      file.setLength(1024 * 1024 * 3)
      val data = new Array[Byte](file.length().toInt)
      file.readFully(data)

      When(s"I invoke PUT envelope/$envelopeId/files/$fileId/$fileRefId")
      val response: WSResponse = upload(data, envelopeId, fileId, fileRefId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }

    scenario("Add multiple files to envelope") {
      Given("I have an envelope-id with an existing file attached")
      val envelopeId = createEnvelope()
      val firstFileId = FileId(s"fileId-${nextId()}")
      val firstFileRefId = FileRefId(s"fileRefId-${nextId()}")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, firstFileId, firstFileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))
      upload("{}".getBytes, envelopeId, firstFileId, firstFileRefId)

      And("And I have a valid new file-id")
      val fileId = FileId(s"fileId-${nextId()}")
      val data = "{}".getBytes

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      When(s"I invoke PUT envelope/$envelopeId/files/$fileId/$fileRefId")
      val response = upload(data, envelopeId, fileId, fileRefId)

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
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("I have a valid file attached to the request body")
      val data = "{}".getBytes

      When(s"I invoke PUT envelope/$envelopeId/files/$fileId/$fileRefId")
      val response: WSResponse = upload(data, envelopeId, fileId, fileRefId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    scenario("Add file with no file attached") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"nofile-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      And("I have no file attached to the request body")
      val data = "".getBytes

      When(s"I invoke PUT envelope/$envelopeId/files/$fileId/$fileRefId")
      val response: WSResponse = upload(data, envelopeId, fileId, fileRefId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
