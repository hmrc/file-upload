package uk.gov.hmrc.fileupload

import java.net.URL

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.fileupload.read.routing.{Algorithm, Audit, Checksum, FileTransferFile, FileTransferNotification, Property, RoutingRepository, ZipData}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FakePushService, FakeFrontendService, IntegrationSpec}

class FileTransferIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FakePushService
     with FakeFrontendService {

  override lazy val pushUrl = Some(pushServiceUrl)
  override lazy val pushDestinations = Some(List("DMS"))

  Feature("File Transfer list") {
    Scenario("List Envelopes for a given destination") {
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

    Scenario("List Envelopes without specifying destination") {
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

  Feature("File Transfer delete") {

    Scenario("Archive Envelope") {
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

  Feature("File Transfer push") {
    Scenario("Request routing for envelopes") {
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

    Scenario("List Envelopes for a given destination") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      val envelopeId = createEnvelope()

      And("The frontend provides a download URL")
      val zipData = ZipData(
          name        = "filename",
          size        = 1L,
          md5Checksum = "4vB/MVHSuPg92a8yDf5IiA==",
          url         = new URL("http://downloadhere")
        )
      stubZipEndpoint(envelopeId, Right(zipData))

      And("The push endpoint acknowledges")
      stubPushEndpoint()

      And("I route an envelope")
      submitRoutingRequest(envelopeId, destination)

      Then("There exist CLOSED envelopes that match it")
      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.body.isEmpty shouldBe false
      }

      And("The push notification was successful")
      verifyPushNotification(FileTransferNotification(
        informationType = destination,
        file            = FileTransferFile(
                            recipientOrSender = None,
                            name              = zipData.name,
                            location          = Some(zipData.url.toString),
                            checksum          = Checksum(Algorithm.Md5, RoutingRepository.base64ToHex(zipData.md5Checksum)),
                            size              = zipData.size.toInt,
                            properties        = List.empty[Property]
                          ),
        audit           = Audit(correlationId = envelopeId.value)
      ))

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
