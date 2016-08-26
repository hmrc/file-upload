package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FakeConsumingService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val consumingServicePort = 8900

  lazy val server = new WireMockServer(wireMockConfig().port(consumingServicePort))

  final lazy val consumimngServiceBaseUrl = s"http://localhost:$consumingServicePort"

  override def beforeAll() = {
    super.beforeAll()
    server.start()
  }

  override def afterAll() = {
    super.afterAll()
    server.stop()
  }

  def stubCallback(callbackPath: String = "mycallbackpath") = {
    server.stubFor(WireMock.get(urlEqualTo(callbackPath))
      .willReturn(WireMock.aResponse().withStatus(200)
      ))
  }

  def verifyQuarantinedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit = {
    happyCallbackEvent(callbackPath, envelopeId, fileId, "QUARANTINED")
  }

  def verifyNoVirusDetectedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit = {
    happyCallbackEvent(callbackPath, envelopeId, fileId, "CLEANED")
  }

  def verifyVirusDetectedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit = {
    sadCallbackEvent(callbackPath, envelopeId, fileId, "ERROR", "VirusDetected")
  }

  def verifyAvailableCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit = {
    happyCallbackEvent(callbackPath, envelopeId, fileId, "AVAILABLE")
  }

  private def happyCallbackEvent(callbackPath: String, envelopeId: EnvelopeId, fileId: FileId, status: String): Unit = {
    server.verify(postRequestedFor(
      urlEqualTo(s"/$callbackPath"))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson( s"""{"envelopeId": "$envelopeId", "fileId": "$fileId", "status": "$status"}""".stripMargin))
    )
  }

  private def sadCallbackEvent(callbackPath: String, envelopeId: EnvelopeId, fileId: FileId, status: String, reason: String): Unit = {
    server.verify(postRequestedFor(
      urlEqualTo(s"/$callbackPath"))
      .withHeader("Content-Type", equalTo("application/json"))
      .withRequestBody(equalToJson(
        s"""
           |{"envelopeId": "$envelopeId", "fileId": "$fileId", "status": "$status", "reason": "$reason"}
        """.stripMargin))
    )
  }

  def callbackUrl(callbackPath: String = "mycallbackpath") = {
    s"$consumimngServiceBaseUrl/$callbackPath"
  }

}
