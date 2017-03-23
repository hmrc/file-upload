package uk.gov.hmrc.fileupload

import play.api.libs.json.JsObject
import uk.gov.hmrc.fileupload.controllers.FileInQuarantineStored
import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport.prettify
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class GetFileMetadataIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  feature("Retrieve Metadata") {

    scenario("GET metadata with valid envelope id") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val json = (requestBodyAsJson() \ "metadata").as[JsObject]
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.jpg", "application/pdf", Some(123L), json))

      eventually {

        When(s"I invoke GET envelopes/$envelopeId/files/$fileId/metadata")
        val response = getFileMetadataFor(envelopeId, fileId)

        Then("I will receive a 200 Ok response")
        response.status shouldBe OK

        And("the response body should contain the file reference details")
        prettify(response.body) shouldBe responseBody(envelopeId, fileId)
      }
    }

    scenario("GET metadata with invalid envelope id") {

      Given("I have an invalid envelope ID")
      val envelopeId = EnvelopeId("invalidEnvelopeId")
      val fileId = FileId("invalidFileID")

      When(s"I invoke GET envelopes/$envelopeId/files/$fileId/metadata")
      val response = getFileMetadataFor(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }
  }
}
