package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeActions, FileUploadSupport, ITestSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.matching.Regex

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  * Currently configured to run against a local instance of the File Upload service
  * Needs to be converted to FakeApplication tests and the contract level acceptance tests re-written
  *
  */
class CreateEnvelopeIntegrationSpec extends FeatureSpec with EnvelopeActions with GivenWhenThen with OneServerPerSuite with ScalaFutures
  with IntegrationPatience with Matchers with Status {

  override lazy val port: Int = 9000

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  feature("Create Envelope") {

    scenario("Create a new Envelope with empty body") {
      Given("I have an empty JSON request")
      val json = "{}"

      When("I invoke POST /file/upload/envelope")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record with no attributes will be created")


      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelope/[A-z0-9-]+$"

    }

    scenario("Create a new Envelope using basic sample") {
      val formattedExpiryDate: String = formatter.print(today)

      Given("the json request")
      val json =
        s"""
           |{"constraints": {
           |    "contentTypes": [
           |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
           |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
           |      "application/vnd.oasis.opendocument.spreadsheet"
           |    ],
           |    "maxItems": 100,
           |    "maxSize": "12GB",
           |    "maxSizePerItem": "10MB"
           |  },
           |  "callbackUrl": "http://absolute.callback.url",
           |  "expiryDate": "$formattedExpiryDate",
           |  "metadata": {
           |    "anything": "the caller wants to add to the envelope"
           |  }
           |}
        """.stripMargin

      When("I invoke POST /file/upload/envelope")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record will be created")

      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelope/[A-z0-9-]+$"
    }

    scenario("Attempt to create an Envelope with an empty request body") {
      pending
      Given("I have an empty request body (ie \"\")")
      val json = ""

      When("I invoke POST /file/upload/envelope")
      val response = createEnvelope(json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST
    }
  }
}
