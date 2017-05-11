package uk.gov.hmrc.fileupload

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

class FileTransferIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  feature("File Transfer list") {

    scenario("List Envelopes for a given destination") {
      Given("I know a destination for envelopes")
      val destination = "DMS"

      And("There exist CLOSED envelopes that match it")
      submitRoutingRequest(createEnvelope(), destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
        val response = getEnvelopesForDestination(Some(destination))

        Then("I will receive a 200 Ok response")
        response.status shouldBe OK

        And("The response will contain expected number of envelopes")
        val body = Json.parse(response.body)
        (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 1
      }
    }

    scenario("List Envelopes without specifying destination") {
      Given("There exist CLOSED envelopes in the DB")

      val expectedNumberOfEnvelopes = 2
      (1 to expectedNumberOfEnvelopes).foreach { _ =>
        submitRoutingRequest(createEnvelope(), "DMS")
      }

      And("There exist envelopes with other statuses")
      createEnvelope()

      eventually {
        When(s"I invoke GET /file-transfer/envelopes (without passing destination")
        val response = getEnvelopesForDestination(None)

        Then("I will receive a 200 Ok response")
        response.status shouldBe OK

        And("The response will contain all envelopes with a CLOSED status")
        val body = Json.parse(response.body)
        (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe expectedNumberOfEnvelopes
      }
    }
  }

  feature("File Transfer delete") {

    scenario("Archive EnvelopeHandler") {
      Given("I know a destination for envelopes")
      val destination = "DMS"

      And("There exist CLOSED envelopes that match it")
      val envelopeId = createEnvelope()
      submitRoutingRequest(envelopeId, destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        When("I archive the envelope")
        archiveEnvelopFor(envelopeId)

        Then(s"I invoke GET /file-transfer/envelopes?destination=$destination")
        val response = getEnvelopesForDestination(Some(destination))

        And("I will receive a 200 Ok response")
        response.status shouldBe OK

        And("The response will contain expected number of envelopes")
        val body = Json.parse(response.body)
        (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0
      }
    }
  }
}
