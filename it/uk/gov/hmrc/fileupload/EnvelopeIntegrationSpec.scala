package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.ws._
import play.api.test._
import uk.gov.hmrc.fileupload.models._

class EnvelopeIntegrationSpec extends PlaySpecification{

  val serverUrl = "http://localhost:9000"

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  var envelopeId : String = null


  "Create Envelope request with basic sample" should {

    "complete successfully with a CREATED response and provide the Envelope ID within the Location header" in new WithServer() {

      val createEnvelopeResourcePath = "/file-upload/envelope"
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

      val finalUrl = serverUrl + createEnvelopeResourcePath

      val response = await(WS.url(finalUrl).post(json))
      response.status must equalTo(CREATED)

      val locationHeader = response.header("Location").get
      envelopeId = locationHeader.substring(locationHeader.lastIndexOf('/')+1)
      println("envelopeId: " + envelopeId)
    }
  }


  "GET Envelope request with a valid Envelope ID" should {

    "complete successfully with an OK response and provide the envelope details in the body" in new WithServer() {

      println("getting envelope for : " + envelopeId)

      val resourcePath = "/file-upload/envelope/{envelopeId}"

      val finalUrl = serverUrl + resourcePath.replace("{envelopeId}", envelopeId)

      val response = await(WS.url(finalUrl).get())
      response.status must equalTo(OK)
      println("***************************")
      println(response.body)
      println("***************************")

      val envelope = Json.fromJson[Envelope](response.json).get
      envelope._id mustEqual envelopeId
    }
  }





}



