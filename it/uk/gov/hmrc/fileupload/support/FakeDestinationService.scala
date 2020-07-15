package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FakeDestinationService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val destinationServicePort = 8901

  private lazy val path = "/asd"

  private lazy val server = new WireMockServer(wireMockConfig().port(destinationServicePort))

  lazy val destinationServiceUrl: String = s"http://localhost:$destinationServicePort$path"

  override def beforeAll() = {
    super.beforeAll()
    server.start()
  }

  override def afterAll() = {
    super.afterAll()
    server.stop()
  }

  def stubPushEndpoint(status: Int = 200) = {
    println(s"Mocking post $destinationServicePort")
    server.stubFor(WireMock.post(WireMock.urlEqualTo(path))
      .willReturn(WireMock.aResponse().withStatus(status)
      ))
  }
}
