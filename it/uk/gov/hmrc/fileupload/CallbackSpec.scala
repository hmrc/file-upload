package uk.gov.hmrc.fileupload

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{equalTo, postRequestedFor, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EnvelopeReportSupport, IntegrationSpec}


class CallbackSpec extends IntegrationSpec with EnvelopeActions {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)


  feature("Event Callbacks") {
    ignore("When an event is received then the consuming service is notified at the callback specified in the envelope") {
      val consumingService = new WireMockServer(wireMockConfig().port(8900))
      val auditingService = new WireMockServer(wireMockConfig().port(8100))

      consumingService.start()
      auditingService.start()

      val callbackUrl = "http://localhost:8900/mycallbackpath"
      val  createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl)))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))

      val response: WSResponse = WS.url(s"$url/events/quarantined")
        .withHeaders("Content-Type" -> "application/json")
        .post( s"""
             | { "envelopeId": "$envelopeId", "fileId": "1" }
          """.stripMargin)
        .futureValue

      response.status shouldBe OK
      consumingService.verify(postRequestedFor(urlEqualTo(callbackUrl)).withHeader("Content-Type", equalTo("application/json")))

      consumingService.stop()
      auditingService.stop()
    }
  }
}