package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

/**
  * Integration tests for FILE-65
  * Delete Envelope
  *
  */

class DeleteEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions {

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
        And("the envelope should be deleted")
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }
    }

    scenario("Delete Envelope - invalid ID with auth") {
      Given("I have an invalid envelope id")
      val invalidEnvelopeId = EnvelopeId()

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(invalidEnvelopeId)


      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }
  }
}