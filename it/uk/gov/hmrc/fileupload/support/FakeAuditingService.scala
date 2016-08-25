package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}

trait FakeAuditingService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val auditingServicePort = 8100

  lazy val auditingServer = new WireMockServer(wireMockConfig().port(auditingServicePort))

  final lazy val auditingServiceBaseUrl = s"http://localhost:$auditingServicePort"

  override def beforeAll() = {
    super.beforeAll()
    auditingServer.start()
  }

  override def afterAll() = {
    super.afterAll()
    auditingServer.stop()
  }

  def stubAuditing() = {
    auditingServer.stubFor(WireMock.get(urlMatching("/*")).willReturn(WireMock.aResponse().withStatus(200)))
  }

}
