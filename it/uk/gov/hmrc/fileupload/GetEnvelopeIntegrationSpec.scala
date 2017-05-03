package uk.gov.hmrc.fileupload

import org.scalatest.time.{Millis, Minutes, Span}
import play.api.libs.json.{JsValue, _}
import uk.gov.hmrc.fileupload.controllers.FileInQuarantineStored
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.QuarantineFile

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  */
class GetEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions with EventsActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Minutes), interval = Span(5, Millis))

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
          case JsDefined(JsString(value)) => value should fullyMatch regex "[A-z0-9-]+"
          case _ => JsError("expectation failed")
        }

        parsedBody \ "status" match {
          case JsDefined(JsString(value)) => value should fullyMatch regex "OPEN"
          case _ => JsError("expectation failed")
        }

        (parsedBody \ "constraints") \ "maxItems" match {
          case JsDefined(JsNumber(const)) =>
            const shouldBe 100
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
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

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
          case JsDefined(JsString(value)) => value should fullyMatch regex "[A-z0-9-]+"
          case _ => JsError("expectation failed")
        }

        parsedBody \ "status" match {
          case JsDefined(JsString(value)) => value should fullyMatch regex "OPEN"
          case _ => JsError("expectation failed")
        }

        (parsedBody \\ "files").head \ "id" match {
          case JsDefined(JsObject(value)) =>
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

    scenario("GET Envelope responds with constraints when") {
      Given("I have an envelope with files")
      val createResponse = createEnvelope(
        s"""{"constraints": {
           |"maxItems": 56,
           |"maxSize": 10485760,
           |"maxSizePerItem": 102400,
           |"contentTypes": ["application/pdf","image/jpeg"]}}""".stripMargin)
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      eventually {
        When("I call GET /file-upload/envelopes/:envelope-id")
        val envelopeResponse = getEnvelopeFor(envelopeId)

        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK
        val json = Json.parse(envelopeResponse.body)

        (json \ "constraints") \ "maxItems" match {
          case JsDefined(JsNumber(const)) =>
            const shouldBe 56
          case _ => JsError("expectation failed")
        }

        (json \ "constraints") \ "maxSizePerItem" match {
          case JsDefined(JsNumber(const)) =>
            const shouldBe 102400
          case _ => JsError("expectation failed")
        }

      }
    }
  }
}
