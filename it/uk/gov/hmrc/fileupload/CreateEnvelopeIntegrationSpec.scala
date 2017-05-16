package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

class CreateEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  val formattedExpiryDate: String = formatter.print(today)

  /**
    * Integration tests for FILE-63 & FILE-64
    * Create Envelope and Get Envelope
    */

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

      Given("I have a default Create Envelope request")
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

  feature("Create Envelope with id") {

    scenario("Create a new Envelope with empty body") {
      Given("I have an empty JSON request")
      val json = "{}"

      When("I invoke PUT /file-upload/envelopes/aaa")
      val response: WSResponse = createEnvelopeWithId("aaa", json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record with no attributes will be created")

      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelopes/aaa"
    }

    scenario("Recreate an envelope") {
      Given("I have an empty JSON request")
      val json = "{}"

      And("I invoke PUT /file-upload/envelopes/aaa")
      createEnvelopeWithId("aaa", json)

      When("I invoke PUT /file-upload/envelopes/aaa")
      val response: WSResponse = createEnvelopeWithId("aaa", json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST
    }

  }


  feature("Create Envelope with Content Type constraints") {

    /**
      * FILE-346 - Envelope Constraints - Content Type
      */

    scenario("Create a new envelope with a valid contentType constraint") {

      Given("a create envelope request constraining xml file types")
      val contentType = s""" "application/xml" """
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "contentType" -> contentType ))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED
    }


    scenario("Create a new envelope with all valid contentType constraints") {

      val validContentTypes =
        s"""   "application/pdf",
           |         "text/xml",
           |         "image/jpeg",
           |         "application/xml",
           |         "application/vnd.ms-excel",
           |         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" """.stripMargin

      Given("a create envelope request constraining all valid file types")
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "contentType" -> validContentTypes))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

    }

    scenario("Create a new envelope with an invalid contentType constraint") {

      Given("a create envelope request with invalid contentType constraint")
      val contentType: String = s""" "application/mumbo" """
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "contentType" -> contentType))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST

      And("The response body will indicate invalid constraint")
      val jsonResponse = Json.parse(response.body)
      val errorMessage = ((jsonResponse \ "error") \ "msg").as[String]
      errorMessage shouldBe "constraints.contentType -> Unsupported Content Type"
    }
  }


  feature("Create Envelope with maxItems constraints") {

    //TODO: refactor to use requestBodyWithConstraints - doesn't currently implement maxItems

    scenario("Create a new Envelope with valid maxItems") {

      Given("a create envelope request with a valid maxItems constraint of 100")
      val json = Json.obj("constraints" -> Json.obj("maxItems" -> 100)).toString()

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED
    }

    scenario("Create a new Envelope with invalid maxItems") {

      Given("a create envelope request with invalid maxItems constraint of 101")
      val json = Json.obj("constraints" -> Json.obj("maxItems" -> 101)).toString()

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST
    }
  }


  feature("Create Envelope with maxSize constraints") {

    scenario("Create a new Envelope with valid maxSize") {

      Given("a create envelope request with valid maxSize constraint of 250MB")
      val maxSize: String = "250MB"
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSize" -> maxSize))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED
    }

    scenario("Create a new Envelope with invalid maxSize") {

      Given("a create envelope request with invalid maxSize constraint of 251MB")
      val maxSize: String = "251MB"
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSize" -> maxSize))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST
    }
  }


  feature("Create Envelope with maxSizePerItem constraints") {

    scenario("Create a new Envelope with valid maxSizePerItem") {

      Given("a create envelope request with valid maxSizePerItem constraint of 100MB")
      val maxSizePerItem: String = "100MB"
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSizePerItem" -> maxSizePerItem))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED
    }

    scenario("Create a new Envelope with invalid maxSizePerItem") {

      Given("a create envelope request with invalid maxSizePerItem constraint of 101MB")
      val maxSizePerItem: String = "101MB"
      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSizePerItem" -> maxSizePerItem))

      When("I invoke POST /file-upload/envelopes")
      val response: WSResponse = createEnvelope(json)

      Then("I will receive a 400 Bad Request response")
      response.status shouldBe BAD_REQUEST
    }
  }

  scenario("Create a new envelope with multiple invalid constraint values") {

    /**
      * FILE-423: Bug raised as this was incorrectly resulting in a HTTP 500 Server error
      */

    Given("a create envelope request with multiple invalid constraints")

    val maxSizePerItem: String = "101MB"
    val maxSize: String = "251MB"
    val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSizePerItem" -> maxSizePerItem, "maxSize" -> maxSize))

    When("I invoke POST /file-upload/envelopes")
    val response: WSResponse = createEnvelope(json)

    Then("I will receive a 400 Bad Request response")
    response.status shouldBe BAD_REQUEST
  }

}
