package uk.gov.hmrc.fileupload

import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import uk.gov.hmrc.fileupload.controllers.EnvelopeActions

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  * Currently configured to run against a local instance of the File Upload service
  * Needs to be converted to FakeApplication tests and the contract level acceptance tests re-written
  *
  */
class GetEnvelopeIntegrationSpec extends FeatureSpec with EnvelopeActions with GivenWhenThen with OneServerPerSuite with ScalaFutures
  with IntegrationPatience with Matchers with Status with BeforeAndAfterEach {

  override lazy val port: Int = 9000

  //override implicit lazy val app: FakeApplication = new FakeApplication(additionalConfiguration = Map("application.router" -> "test.Routes"))

  feature("Retrieve Envelope") {

    scenario("GET Envelope responds with an ID") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val locationHeader = createResponse.header("Location").get
      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)

      When("I call GET /file-upload/envelope/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)


      Then("I will receive a 200 Ok response")
      envelopeResponse.status shouldBe OK

      And("the response body should contain the envelope details")
      envelopeResponse.body shouldNot be(null)
    }

    scenario("GET envelope using invalid ID") {
      Given("I have an invalid envelope id")
      val envelopeId = "123435"

      When("I call GET /file-upload/envelope/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }
  }
}
