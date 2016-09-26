package uk.gov.hmrc.fileupload

import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}


class DownloadEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  feature("Download Envelope") {

    scenario("A client can download an envelope including its file") {
      Given("I have an envelope with files")
      val envelopeId = createEnvelope()
      val data = "my file content"
      val fileId = FileId(s"fileId-${nextId()}")
      val fileRefId = FileRefId()

      And("File has been stored in quarantine on the front-end")
      sendFileInQuarantineStored(
        FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "file-name", "contentType", Json.obj("metadata" -> "foo"))
      )

      And("File was scanned and no virus was found")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      And("File was stored in transient")
      upload(data.getBytes, envelopeId, fileId, fileRefId)

      And("Routing request was submitted")
      submitRoutingRequest(envelopeId, "DMS")

      eventually {
        When(s"I invoke GET file-transfer/envelope/$envelopeId")
        val response: WSResponse = downloadEnvelope(envelopeId)

        Then("I will receive a 200 OK response")
        withClue(response.body) {
          response.status shouldBe OK
        }

        And("response should include content type")
        response.header("Content-Type") shouldBe Some("application/zip")

        And("response should be chunked")
        response.header("Transfer-Encoding") shouldBe Some("chunked")
      }
    }

  }
}
