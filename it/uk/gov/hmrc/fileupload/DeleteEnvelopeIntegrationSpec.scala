package uk.gov.hmrc.fileupload

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.FileScanned
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.QuarantineFile

/**
  * Integration tests for FILE-65
  * Delete Envelope
  *
  */

class DeleteEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions with EventsActions {

  feature("Delete Envelope") {

    scenario("Delete Envelope - with wrong Auth") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      When("I call DELETE but with wrong auth /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopWithWrongAuth(envelopeId)

      Then("I will receive a 403 OK response")
      envelopeResponse.status shouldBe FORBIDDEN

    }

    scenario("Delete Envelope - valid with auth") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(envelopeId)

      Then("I will receive a 200 OK response")
      envelopeResponse.status shouldBe OK

      eventually {
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }

      And("the envelope should be deleted")
      val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
      checkEnvelopeDeleted.status shouldBe NOT_FOUND
    }

    scenario("Delete Envelope - invalid ID with auth") {
      Given("I have an invalid envelope id")
      val invalidEnvelopeId = EnvelopeId()

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(invalidEnvelopeId)


      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }

    scenario("Delete an envelope and all files in the Envelope") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)

      And("I have all valid ids")
      val envelopeId = envelopeIdFromHeader(createResponse)
      val fileId = FileId(s"fileId-${nextId()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("File was scanned and virus was found")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, true))

      Then("File should in the progress files list")
      eventually {
        val listShouldBe = Json.obj("_id" -> fileRefId.value, "envelopeId" -> envelopeId.value, "fileId" -> fileId.value, "startedAt" -> 0)
        getInProgressFiles().body shouldBe s"[$listShouldBe]"
      }

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(envelopeId)

      Then("I will receive a 200 OK response")
      envelopeResponse.status shouldBe OK

      eventually {
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }

      And("File is not in the progress files list")
      getInProgressFiles().body shouldBe "[]"
    }
  }
}
