package uk.gov.hmrc.fileupload

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

/**
  * Integration tests for FILE-65
  * Delete Envelope
  *
  */
class DeleteEnvelopeIntegrationSpec extends IntegrationSpec with Eventually with EnvelopeActions with BeforeAndAfterEach{

  override implicit lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
    ))

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }


  feature("Delete Envelope") {

    scenario("Delete Envelope - valid") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(envelopeId)

      Then("I will receive a 202 Accpeted response")
      envelopeResponse.status shouldBe ACCEPTED

      eventually {
        And("the envelope should be deleted")
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }
    }

    scenario("Delete Envelope - invalid ID") {
      Given("I have an invalid envelope id")
      val invalidEnvelopeId = EnvelopeId()

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(invalidEnvelopeId)

      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }
  }
}