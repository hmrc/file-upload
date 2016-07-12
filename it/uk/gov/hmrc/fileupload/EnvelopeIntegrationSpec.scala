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
class EnvelopeIntegrationSpec extends FeatureSpec with EnvelopeActions with GivenWhenThen with OneServerPerSuite with ScalaFutures
  with IntegrationPatience with Matchers with Status {

  override lazy val port: Int = 9000

  val CreateEnvelopeResourcePath = "/file-upload/envelope"
  val GetEnvelopeResourcePath = "/file-upload/envelope/:envelopeId"
  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

//  "Create Envelope request with basic sample" should {
//    "complete successfully with a 201 Created response and provide the Envelope ID within the Location header" in {
//      val formattedExpiryDate: String = formatter.print(today)
//      val response: WSResponse = await {
//        support.createEnvelope(
//          s"""
//             |{"constraints": {
//             |    "contentTypes": [
//             |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
//             |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
//             |      "application/vnd.oasis.opendocument.spreadsheet"
//             |    ],
//             |    "maxItems": 100,
//             |    "maxSize": "12GB",
//             |    "maxSizePerItem": "10MB"
//             |  },
//             |  "callbackUrl": "http://absolute.callback.url",
//             |  "expiryDate": "$formattedExpiryDate",
//             |  "metadata": {
//             |    "anything": "the caller wants to add to the envelope"
//             |  }
//             |}
//        """.stripMargin)
//      }
//
//      response.status shouldBe CREATED
//      val locationHeader = response.header("Location").get
//      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
//      envelopeId shouldNot be(null)
//    }
//  }

  feature("Create Envelope") {

    scenario("Create a new Envelope with empty body") {
      Given("I have an empty JSON request")
      val json = "{}"

      When("I invoke POST /file/upload/envelope")
      val response: WSResponse = createEnvelope("{}")

      Then("I will receive a 201 Created response")
      response.status shouldBe CREATED

      And("a new Envelope record with no attributes will be created")


      And("the Envelope ID will be returned in the location header")
      val locationHeader = response.header("Location").get
      locationHeader should fullyMatch regex ".*/file-upload/envelope/[A-z0-9-]+$"

    }

  }


//  "Create Envelope request with {} body" should {
//    "completed successfully with a 201 Created response and provide the Envelope ID within the Location header" in {
//      val response: WSResponse = await {
//        support.createEnvelope("{}")
//      }
//      response.status shouldBe CREATED
//
//      val locationHeader = response.header("Location").get
//      locationHeader should fullyMatch regex ".*/file-upload/envelope/[A-z0-9-]+$"
//
//    }
//  }
  //
  //  "Create Envelope request with null body" should {
  //    "fail with a 400 Bad Request error" in {
  //      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
  //      val response = await(WS.url(finalUrl).post(null))
  //      response.status shouldBe equal(BAD_REQUEST)
  //    }
  //  }
  //
  //  "Create Envelope request which contains an Envelope ID" should {
  //    "fail with a 400 Bad Request error" in {
  //      val formattedExpiryDate: String = formatter.print(today)
  //      val json = Json.parse(
  //        s"""
  //           |{"_id": "92e8fcdd-2911-464f-8b52-8fb1f05206fb",
  //           |"constraints": {
  //           |    "contentTypes": [
  //           |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  //           |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  //           |      "application/vnd.oasis.opendocument.spreadsheet"
  //           |    ],
  //           |    "maxItems": 100,
  //           |    "maxSize": "12GB",
  //           |    "maxSizePerItem": "10MB"
  //           |  },
  //           |  "callbackUrl": "http://absolute.callback.url",
  //           |  "expiryDate": "$formattedExpiryDate",
  //           |  "metadata": {
  //           |    "anything": "the caller wants to add to the envelope"
  //           |  }
  //           |}
  //        """.stripMargin)
  //
  //      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
  //      val response = await(WS.url(finalUrl).post(json))
  //      response.status shouldNot be(BAD_REQUEST)
  //    }
  //  }
  //
  //  "GET Envelope request with a valid Envelope ID" should {
  //    "complete successfully with an OK response and provide the envelope details in the body" in {
  //
  //      // Create an envelope to retrieve an envelope ID
  //      val finalCreateUrl = ServerUrl + CreateEnvelopeResourcePath
  //      val createResponse = await(WS.url(finalCreateUrl).post(Json.parse("{}")))
  //      createResponse.status should equal(CREATED)
  //      val locationHeader = createResponse.header("Location").get
  //      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
  //
  //      // Retrieve envelope ID
  //      val finalGetUrl = ServerUrl + GetEnvelopeResourcePath.replace(":envelopeId",envelopeId)
  //      val getResponse = await(WS.url(finalGetUrl).get())
  //      getResponse.status should equal(OK)
  //      getResponse.body shouldNot be(null)
  //    }
  //  }


}



