package uk.gov.hmrc.fileupload

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FakeDestinationService, IntegrationSpec}

class FileTransferIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FakeDestinationService {

  override lazy val dmsServiceUrl = Some(destinationServiceUrl)

  feature("File Transfer list") {
    scenario("List Envelopes for a given destination") {
      Given("I use a destination not configured for push")
      val destination = "NO-PUSH"

      And("There exist CLOSED envelopes that match it")
      submitRoutingRequest(createEnvelope(), destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
      }

      When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain expected number of envelopes")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 1
    }

    scenario("List Envelopes without specifying destination") {
      Given("There exist CLOSED envelopes in the DB")
      val destination = "NO-PUSH"

      val expectedNumberOfEnvelopes = 2
      (1 to expectedNumberOfEnvelopes).foreach { _ =>
        submitRoutingRequest(createEnvelope(), destination)
      }

      And("There exist envelopes with other statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
      }

      When(s"I invoke GET /file-transfer/envelopes (without passing destination")
      val response = getEnvelopesForDestination(None)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain all envelopes with a CLOSED status")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe expectedNumberOfEnvelopes
    }
  }

  feature("File Transfer delete") {

    scenario("Archive Envelope") {
      Given("I know a destination for envelopes")
      val destination = "NO-PUSH"

      And("There exist CLOSED envelopes that match it")
      val envelopeId = createEnvelope()
      submitRoutingRequest(envelopeId, destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
      }

      When("I archive the envelope")
      archiveEnvelopFor(envelopeId)

      eventually {
        val response = getEnvelopesForDestination(Some(destination))
        response.status shouldBe OK
      }

      Then(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      And("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain expected number of envelopes")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0
    }
  }

  feature("File Transfer push") {
    scenario("Request routing for envelopes") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      And("The push endpoint does not acknowledge")
      stubPushEndpoint(status = 500)

      And("I route an envelope")
      submitRoutingRequest(createEnvelope(), destination)

      Then("There exist ROUTE_REQUESTED envelopes that match it")
      val response = getEnvelopesForStatus(status = List("ROUTE_REQUESTED"), inclusive = true)
      response.body.isEmpty shouldBe false
    }

    scenario("List Envelopes for a given destination") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      And("The push endpoint acknowledges")
      stubPushEndpoint()

      And("I route an envelope")
      submitRoutingRequest(createEnvelope(), destination)

      Then("There exist CLOSED envelopes that match it")
      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.body.isEmpty shouldBe false
      }

      When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("It will not include the pushed envelope")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0
    }
  }
}
