package uk.gov.hmrc.fileupload

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

class SealEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  override lazy val port: Int = 9000

  feature("Seal Envelope") {

    scenario("Sealing an envelope") {
      Given("I have a valid envelope id")
      val envelope: String = createEmptyEnvelope()

      When("I seal an envelope")
      val response: WSResponse = seal(envelope)

      Then("I should receive a 200 OK response")
      response.status shouldBe OK

      val sealedEnvelope: WSResponse = getEnvelopeFor(envelope)
      sealedEnvelope.body should fullyMatch regex """.*"status":"Sealed".*"""
    }

  }
}
