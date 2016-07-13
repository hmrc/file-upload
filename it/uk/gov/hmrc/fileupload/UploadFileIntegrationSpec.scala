package uk.gov.hmrc.fileupload

import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-...
  * Upload File
  *
  */
class UploadFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Upload File") {

    scenario("Upload a file") {
      Given("I have a valid envelope id")
      val envelopeId: String = createEnvelope()

      And("I have a file")
      val data = "{}".getBytes
      val fileId = s"fileId-${nextId()}"

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 200 Created response")
      response.status shouldBe OK
    }
  }
}
