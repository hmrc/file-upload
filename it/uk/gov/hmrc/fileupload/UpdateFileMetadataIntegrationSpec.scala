package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._

/**
  * Integration tests for FILE-...
  * Update FileMetadata
  *
  */
class UpdateFileMetadataIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Update Metadata") {

    scenario("Update metadata") {
      Given("I have a json")
      val json = requestBody()

      And("ids")
      val envelopeId = s"envelopeId-${nextId()}"
      val fileId = s"fileId-${nextId()}"

      When(s"I invoke POST envelope/$envelopeId/file/$fileId/metadata")
      val response = updateFileMetadata(json, envelopeId, fileId)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK
    }
  }
}
