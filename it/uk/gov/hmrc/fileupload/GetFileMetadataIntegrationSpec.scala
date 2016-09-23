package uk.gov.hmrc.fileupload

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.controllers.FileInQuarantineStored
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.fileupload.support.EnvelopeReportSupport.prettify
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class GetFileMetadataIntegrationSpec extends IntegrationSpec with Eventually with FileActions with EnvelopeActions with EventsActions with BeforeAndAfterEach {

  override implicit lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
    ))

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  feature("Retrieve Metadata") {

    scenario("GET metadata with valid envelope id") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val json = (requestBodyAsJson() \ "metadata").as[JsObject]
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.jpg", "application/pdf", json))

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
