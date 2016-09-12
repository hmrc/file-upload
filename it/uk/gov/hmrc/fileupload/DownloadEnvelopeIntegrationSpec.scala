package uk.gov.hmrc.fileupload

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}


class DownloadEnvelopeIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

  feature("Download Envelope") {

    scenario("A client can download an envelope including its file") {
      Given("I have an envelope with files")
      val envelopeId = createEnvelope()
      val data = "my file content"
      val fileId = FileId(s"fileId-${nextId()}")
      upload(data.getBytes, envelopeId, fileId)

      When(s"I invoke GET file-transfer/envelope/$envelopeId")
      val response: WSResponse = downloadEnvelope(envelopeId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK

      And("response should include content type")
      response.header("Content-Type") shouldBe Some("application/zip")

      And("response should be chunked")
      response.header("Transfer-Encoding") shouldBe Some("chunked")
    }

  }
}
