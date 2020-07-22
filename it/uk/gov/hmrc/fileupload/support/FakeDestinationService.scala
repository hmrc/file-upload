package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FakeDestinationService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val destinationServicePort = 8901

  private lazy val path = "/notification/fileready"

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

  def stubPushEndpoint(status: Int = 200) =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status))
    )

  def verifyPushNotification(envelopeId: EnvelopeId): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(path))
        .withHeader("Content-Type", containing("application/json"))
        .withRequestBody(equalToJson(
          s"""{"informationType":"String","file":{"recipientOrSender":"String","name":"String","location":"https://file-upload.public/file-transfer/envelopes/$envelopeId","checksum":{"algorithm":"md5","value":"0"},"size":0,"properties":[]},"audit":{"correlationID":"$envelopeId"}}"""))
    )
}
