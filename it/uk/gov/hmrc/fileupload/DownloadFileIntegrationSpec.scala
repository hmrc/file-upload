package uk.gov.hmrc.fileupload

import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-104
  * Download File
  *
  */
class DownloadFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions {

  feature("Download File") {

    scenario("Check that a file can be downloaded") {
      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have uploaded a file")
      val data = "{'name':'pete'}"
      val fileId = FileId(s"fileId-${nextId()}")
      upload(data.getBytes, envelopeId, fileId)

      When(s"I invoke GET envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 Created response")
      response.status shouldBe OK

      And("I will receive the file")
      response.body shouldBe data

      And("Header should include content length")
      response.header("Content-Length") shouldBe Some(s"${data.getBytes.length}")

      And("Header should include content disposition")
      response.header("Content-Disposition") shouldBe Some("attachment; filename=\"data\"")
    }

    scenario("File can not be found") {
      Given("I have a valid envelope id")
      val envelopeId = createEnvelope()

      And("I have an invalid file id")
      val fileId = FileId(s"fileId-${nextId()}")

      When(s"I invoke GET envelope/$envelopeId/file/$fileId/content")
      val response: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 404 Not Found response")
      response.status shouldBe NOT_FOUND
    }
  }
}
