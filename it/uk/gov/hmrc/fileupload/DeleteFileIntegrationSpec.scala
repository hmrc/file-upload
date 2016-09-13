package uk.gov.hmrc.fileupload

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-180
  * Delete File
  *
  */
class DeleteFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

  feature("Delete file") {

    scenario("Delete an existing file") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("I uploaded a file")
      upload("abc".getBytes(), envelopeId, fileId, fileRefId)

      When(s"I invoke DELETE envelope/$envelopeId/file/$fileId")
      val response: WSResponse = delete(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
