package uk.gov.hmrc.fileupload

import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-...
  * Upload File
  *
  * Currently configured to run against a local instance of the File Upload service
  * Needs to be converted to FakeApplication tests and the contract level acceptance tests re-written
  *
  */
class UploadFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Upload File") {

    scenario("Upload a file") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      And("I have a file")
      val data = "{}".getBytes
      val fileId = "fileId-123"

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = upload(data, envelopeId, fileId)

      Then("I will receive a 200 Created response")
      response.status shouldBe OK
    }
  }
}
