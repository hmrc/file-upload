/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.{JsValue, _}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport.requestBodyWithConstraints
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.QuarantineFile

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  */
class GetEnvelopeIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with EventsActions
     with IntegrationPatience {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  Feature("Retrieve Envelope") {

    Scenario("GET Envelope responds with an ID") {
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

    Scenario("GET Envelope responds with a list of files when envelope not empty") {
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

    Scenario("GET envelope using invalid ID") {
      Given("I have an invalid envelope id")
      val envelopeId = EnvelopeId()

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }

    Scenario("GET Envelope responds with constraints on maxItems, maxSize, maxSizePerItem and contentTypes") {
      val formattedExpiryDate: String = formatter.print(today)

      Given("I have an envelope with constraints on maxSize and maxSizePerItem but NOT maxItems")

      val maxSize: String = "100MB"
      val maxSizePerItem : String = "10MB"

      val json = requestBodyWithConstraints(Map("formattedExpiryDate" -> formattedExpiryDate, "maxSize" -> maxSize, "maxSizePerItem" -> maxSizePerItem))
      val createResponse: WSResponse = createEnvelope(json)
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      var envelopeResponse: WSResponse = null
      eventually {
        When("I call GET /file-upload/envelopes/:envelope-id")
        envelopeResponse = getEnvelopeFor(envelopeId)

        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK
      }

      And("The envelope details will include the constraints as they were applied")
      val jsonResponse = Json.parse(envelopeResponse.body)

      val actualMaxSizePerItem = ((jsonResponse \ "constraints") \ "maxSizePerItem").as[String]
      actualMaxSizePerItem shouldBe "10MB"

      val actualMaxSize = ((jsonResponse \ "constraints") \ "maxSize").as[String]
      actualMaxSize shouldBe "100MB"

      And("the default maxItems of 100 should be applied")
      val actualMaxItems = ((jsonResponse \ "constraints") \ "maxItems").as[Int]
      actualMaxItems shouldBe 100
    }
  }

  Feature("Create Envelope without constraints") {

    Scenario("Create a new Envelope without constraints") {
      Given("a create envelope request without any constraint")
      val json = "{}"

      When("I invoke POST /file-upload/envelopes")
      val createEnvelopeResponse: WSResponse = createEnvelope(json)

      Then("I will receive a 201 Created response")
      createEnvelopeResponse.status shouldBe CREATED

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeId = envelopeIdFromHeader(createEnvelopeResponse)

      var getEnvelopeResponse: WSResponse = null
      eventually {
        getEnvelopeResponse = getEnvelopeFor(envelopeId)
        getEnvelopeResponse.status shouldBe OK
      }

      And("the constraints should be the defaults")
      val parsedBody = Json.parse(getEnvelopeResponse.body)

      And("the default maxSizePerItem of 10MB should be applied")
      val actualMaxSizePerItem = ((parsedBody \ "constraints") \ "maxSizePerItem").as[String]
      actualMaxSizePerItem shouldBe "10MB"

      And("the default maxSize of 25MB should be applied")
      val actualMaxSize = ((parsedBody \ "constraints") \ "maxSize").as[String]
      actualMaxSize shouldBe "25MB"

      And("the default maxItems of 100 should be applied")
      val actualMaxItems = ((parsedBody \ "constraints") \ "maxItems").as[Int]
      actualMaxItems shouldBe 100
    }
  }
}
