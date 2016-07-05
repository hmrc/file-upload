package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.ws._
import play.api.test._
import uk.gov.hmrc.fileupload.models._
import org.scalatest._

/**
  * Integration tests for FILE-63 & FILE-64
  * Create Envelope and Get Envelope
  *
  * Currently configured to run against a local instance of the File Upload service
  * Needs to be converted to FakeApplication tests and the contract level acceptance tests re-written
  *
  */
class EnvelopeIntegrationSpec extends PlaySpecification {

  val ServerUrl = "http://localhost:9000"
  val CreateEnvelopeResourcePath = "/file-upload/envelope"
  val GetEnvelopeResourcePath = "/file-upload/envelope/:envelopeId"
  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  "Create Envelope request with basic sample" should {
    "complete successfully with a 201 Created response and provide the Envelope ID within the Location header" in new WithServer() {
      val formattedExpiryDate: String = formatter.print(today)
      val json = Json.parse(
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
        """.stripMargin)
      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
      val response = await(WS.url(finalUrl).post(json))
      response.status must equalTo(CREATED)
      val locationHeader = response.header("Location").get
      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
      envelopeId mustNotEqual null
      println(s"envelopeId: $envelopeId")
    }
  }

  "Create Envelope request with {} body" should {
    "completed successfully with a 201 Created response and provide the Envelope ID within the Location header" in new WithServer() {
      val json = Json.parse("{}")
      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
      val response = await(WS.url(finalUrl).post(json))
      response.status must equalTo(CREATED)
      val locationHeader = response.header("Location").get
      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
      println(s"envelopeId: $envelopeId")
    }
  }

  "Create Envelope request with null body" should {
    "fail with a 400 Bad Request error" in new WithServer() {
      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
      val response = await(WS.url(finalUrl).post(null))
      response.status must equalTo(BAD_REQUEST)
    }
  }

  "Create Envelope request which contains an Envelope ID" should {
    "fail with a 400 Bad Request error" in new WithServer() {
      val formattedExpiryDate: String = formatter.print(today)
      val json = Json.parse(
        s"""
           |{"_id": "92e8fcdd-2911-464f-8b52-8fb1f05206fb",
           |"constraints": {
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
        """.stripMargin)

      val finalUrl = ServerUrl + CreateEnvelopeResourcePath
      val response = await(WS.url(finalUrl).post(json))
      response.status must equalTo(BAD_REQUEST)
    }
  }

  "GET Envelope request with a valid Envelope ID" should {
    "complete successfully with an OK response and provide the envelope details in the body" in new WithServer() {

      // Create an envelope to retrieve an envelope ID
      val finalCreateUrl = ServerUrl + CreateEnvelopeResourcePath
      val createResponse = await(WS.url(finalCreateUrl).post(Json.parse("{}")))
      createResponse.status must equalTo(CREATED)
      val locationHeader = createResponse.header("Location").get
      val envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1)

      // Retrieve envelope ID
      val finalGetUrl = ServerUrl + GetEnvelopeResourcePath.replace(":envelopeId",envelopeId)
      val getResponse = await(WS.url(finalGetUrl).get())
      getResponse.status must equalTo(OK)
      getResponse.body mustNotEqual(null)
    }
  }







}



