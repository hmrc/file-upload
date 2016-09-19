package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.Eventually
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.FileInQuarantineStored
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, IntegrationSpec}

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  */
class GetEnvelopeIntegrationSpec extends IntegrationSpec with Eventually with EnvelopeActions with EventsActions {

  feature("Retrieve Envelope") {

    scenario("GET Envelope responds with an ID") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      eventually {
        When("I call GET /file-upload/envelopes/:envelope-id")
        val envelopeResponse = getEnvelopeFor(envelopeId)

        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK

        And("the response body should contain the envelope details")
        val body: String = envelopeResponse.body
        body shouldNot be(null)

        val parsedBody: JsValue = Json.parse(body)
        parsedBody \ "id" match {
          case JsString(value) =>  value should fullyMatch regex "[A-z0-9-]+"
          case _ => JsError("expectation failed")
        }

        parsedBody \ "status" match {
          case JsString(value) =>  value should fullyMatch regex "OPEN"
          case _ => JsError("expectation failed")
        }
      }

    }

    scenario("GET Envelope responds with a list of files when envelope not empty") {
      Given("I have an envelope with files")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)
      val fileId = FileId("myfileid")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))

      eventually {
        When("I call GET /file-upload/envelopes/:envelope-id")
        val envelopeResponse = getEnvelopeFor(envelopeId)

        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK

        And("the response body should contain the envelope details")
        val body: String = envelopeResponse.body
        body shouldNot be(null)

        val parsedBody: JsValue = Json.parse(body)
        parsedBody \ "id" match {
          case JsString(value) => value should fullyMatch regex "[A-z0-9-]+"
          case _ => JsError("expectation failed")
        }

        parsedBody \ "status" match {
          case JsString(value) => value should fullyMatch regex "OPEN"
          case _ => JsError("expectation failed")
        }

        (parsedBody \ "files").asInstanceOf[JsArray].value.head \ "id" match {
          case JsObject(value) =>
            value shouldBe fileId
          case _ => JsError("expectation failed")
        }
      }

    }

    scenario("GET envelope using invalid ID") {
      Given("I have an invalid envelope id")
      val envelopeId = EnvelopeId()

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }
  }
}
