package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport.prettify
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class GetFileMetadataIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Retrieve Metadata") {

    scenario("GET metadata with valid envelope id") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a file with metadata already set")
      val json = requestBody()
      updateFileMetadata(json, envelopeId, fileId)

      When(s"I invoke GET envelope/$envelopeId/file/$fileId/metadata")
      val response = getFileMetadataFor(envelopeId, fileId)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("the response body should contain the file reference details")
      prettify(response.body) shouldBe responseBody(envelopeId, fileId)
    }

    scenario("GET metadata with invalid envelope id") {

      Given("I have an invalid envelope ID")
      val envelopeId = EnvelopeId("invalidEnvelopeId")
      val fileId = FileId("invalidFileID")

      When(s"I invoke GET envelope/$envelopeId/file/$fileId/metadata")
      val response = getFileMetadataFor(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }
  }
}
