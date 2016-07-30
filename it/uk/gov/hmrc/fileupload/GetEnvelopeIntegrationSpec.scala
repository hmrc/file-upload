package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  */
class GetEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  feature("Retrieve Envelope") {

    scenario("GET Envelope responds with an ID") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      When("I call GET /file-upload/envelope/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I will receive a 200 Ok response")
      envelopeResponse.status shouldBe OK

      And("the response body should contain the envelope details")
      envelopeResponse.body shouldNot be(null)
    }

    scenario("GET envelope using invalid ID") {
      Given("I have an invalid envelope id")
      val envelopeId = EnvelopeId()

      When("I call GET /file-upload/envelope/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }
  }
}
