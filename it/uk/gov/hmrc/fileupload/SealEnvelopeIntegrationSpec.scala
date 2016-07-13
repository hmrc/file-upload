package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.controllers.EnvelopeActions

class SealEnvelopeIntegrationSpec extends FeatureSpec with EnvelopeActions with GivenWhenThen with OneServerPerSuite with ScalaFutures
  with IntegrationPatience with Matchers with Status {

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
