package uk.gov.hmrc.fileupload

import java.io.RandomAccessFile

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.JsObject
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-83
  * Upload File
  *
  */
class UploadFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

  feature("Upload File") {

    info("As a DFS consumer of the file upload service")
    info("I want to upload a file")
    info("So that I can persist it in the the database")

    scenario("Add file to envelope (valid)") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file attached to the request body")
      val data = "{}".getBytes

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }

    scenario("Add valid 3MB file to envelope") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid 3MB file attached to the request body")
      val file = new RandomAccessFile("t", "rw")
      file.setLength(1024 * 1024 * 3)
      val data = new Array[Byte](file.length().toInt)
      file.readFully(data)

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }

    scenario("Add multiple files to envelope") {
      Given("I have an envelope-id with an existing file attached")
      val envelopeId = createEnvelope()
      val firstFileId = FileId(s"fileId-${nextId()}")
      upload("{}".getBytes, envelopeId, firstFileId)

      And("And I have a valid new file-id")
      val fileId = FileId(s"fileId-${nextId()}")
      val data = "{}".getBytes

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response = upload(data, envelopeId, fileId)

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

      And("I have a valid file attached to the request body")
      val data = "{}".getBytes

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    scenario("Add file with no file attached") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"nofile-${nextId()}")

      And("I have no file attached to the request body")
      val data = "".getBytes

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
