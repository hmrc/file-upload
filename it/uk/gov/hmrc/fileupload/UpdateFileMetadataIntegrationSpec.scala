package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class UpdateFileMetadataIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Update Metadata") {

    scenario("Create File Metadata - Basic example") {
      Given("I have a valid envelope ID")
      And("a valid file ID")
      val envelopeId = createEnvelope()
      val fileId = s"fileId-${nextId()}"

      And("I have a JSON body with an example PUT File Metadata request ")
      val json = requestBody()

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/metadata")
      val response = updateFileMetadata(json, envelopeId, fileId)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK
    }

    scenario("Create File Metadata - empty body") {
      Given("I have a valid envelope ID")
      And("a valid file ID")
      val envelopeId = createEnvelope()
      val fileId = s"fileId-${nextId()}"

      And("I have a JSON body {}")
      val json = requestBody()

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/metadata")
      val response = updateFileMetadata(json, envelopeId, fileId)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK
    }

    scenario("Create File Metadata - invalid Envelope ID") {
      pending
      Given("I have a invalid envelope ID")
      And("a valid file ID")
      val envelopeId = s"envelopeId-${nextId()}"
      val fileId = s"fileId-${nextId()}"

      And("I have a JSON body with an example PUT File Metadata request ")
      val json = requestBody()

      When(s"I invoke PUT envelope/$envelopeId/file/$fileId/metadata")
      val response = updateFileMetadata(json, envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }

    scenario("Create File Metadata for envelope with a file attachment") {
      Given("I have a valid envelope ID and a valid file ID")
      val envelopeId = createEnvelope()
      val fileId = s"fileId-${nextId()}"

      And("I have a file already attached to the envelope")
      upload("test".getBytes, envelopeId, fileId)

      And("I have a JSON body with an example PUT File Metadata request ")
      val json = requestBody()

      When(s"I call PUT /file-upload/envelope/$envelopeId/file/$fileId/metadata")
      val response = updateFileMetadata(json, envelopeId, fileId)

      Then("I should receive a 200 OK response")
      response.status shouldBe OK
    }

  }
}
