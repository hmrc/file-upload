package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}

trait FakeFrontendService extends BeforeAndAfterAll {
  this: Suite =>

  lazy val mockFEServicePort = 8017

  lazy val mockFEServer = new WireMockServer(wireMockConfig().port(mockFEServicePort))

  override def beforeAll() = {
    super.beforeAll()
    mockFEServer.start()
  }

  override def afterAll() = {
    super.afterAll()
    mockFEServer.stop()
  }
}
