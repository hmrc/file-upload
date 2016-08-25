package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.concurrent.Eventually
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support._


class CallbackSpec extends IntegrationSpec with EnvelopeActions with Eventually with FakeConsumingService with FakeAuditingService {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  feature("Event Callbacks") {
    scenario("When quarantine event is received then the consuming service is notified at the callback specified in the envelope") {

      val callbackPath = "mycallbackpath"
      stubCallback(callbackPath)

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl(callbackPath))))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
      val fileId = FileId("1")

      val response: WSResponse = WS.url(s"$url/events/quarantined")
        .withHeaders("Content-Type" -> "application/json")
        .post(
          s"""
             | { "envelopeId": "$envelopeId", "fileId": "$fileId" }
          """.stripMargin)
        .futureValue

      response.status shouldBe OK
      eventually { verifyQuarantinedCallbackReceived(callbackPath, envelopeId, fileId ) }

    }
  }
}