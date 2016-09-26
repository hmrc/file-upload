package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  */
class CreateEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  feature("Create Envelope") {

    scenario("Create a new Envelope with empty body") {
      Given("I have an empty JSON request")
      val json = "{}"

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record with no attributes will be created")

      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelopes/[A-z0-9-]+$"
    }

    scenario("Create a new Envelope using basic sample") {
      val formattedExpiryDate: String = formatter.print(today)

      Given("I have the following JSON")
      val json = requestBody(Map("formattedExpiryDate" -> formattedExpiryDate))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record will be created")

      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelopes/[A-z0-9-]+$"
    }
  }
}
