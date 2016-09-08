package uk.gov.hmrc.fileupload

import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.envelope.{EnvelopeStatusClosed, EnvelopeStatusOpen, Repository}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

class FileTransferIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions with BeforeAndAfterEach {

  override implicit lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
    ))

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  feature("File Transfer") {

    scenario("List Envelopes for a given destination") {
      Given("I know a destination for envelopes")
      val destination = "DMS"

      And("There exist CLOSED envelopes that match it")
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusClosed, destination)

      And("There exist other envelopes with different statuses and destinations")
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusClosed, "not matching destination")
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusOpen, destination)

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
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusClosed, "dest1")
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusClosed, "dest2")

      And("There exist envelopes with other statuses")
      creteEnvelopeWithStatusAndDestination(EnvelopeStatusOpen, "dest1")

      When(s"I invoke GET /file-transfer/envelopes (without passing destination")
      val response = getEnvelopesForDestination(None)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain all envelopes with a CLOSED status")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 2
    }

  }
}
