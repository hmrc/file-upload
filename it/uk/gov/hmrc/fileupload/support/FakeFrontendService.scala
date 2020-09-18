package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.routing.ZipData
import play.api.libs.json.Json

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

  def stubZipEndpoint(envelopeId: EnvelopeId, result: Either[Int, ZipData]) =
    mockFEServer.stubFor(
      post(urlEqualTo(s"/internal-file-upload/zip/envelopes/${envelopeId.value}"))
        .willReturn(
          result match {
            case Left(status) => aResponse().withStatus(status)
            case Right(zipData) => implicit val zrf = ZipData.format
                                   aResponse()
                                     .withStatus(200)
                                     .withBody(Json.toJson(zipData).toString)
          }
        )
    )
}
