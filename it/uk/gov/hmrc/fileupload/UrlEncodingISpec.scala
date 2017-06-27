package uk.gov.hmrc.fileupload

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

class UrlEncodingISpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions with FakeFrontendService{

  val data = "{'name':'%2520'}"

  feature("Odd Url Encoding") {

    scenario("Retrieve File Metadata with a FileId containing %2520c") {

      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}" + "%2520c")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      When("Retrieve File Details")
      val fileResponse = getFileMetadataFor(envelopeId,fileId)

      Then("Receive 200")
      fileResponse.status shouldBe OK
    }

    scenario("Retrieve File Metadata with %2c as part of FileId") {

      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}" + "%2c")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      val storeFile = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      When("Retrieve Envelope Details")
      val envelopesResponse = getEnvelopeFor(envelopeId)
      val parsedBody: JsValue = Json.parse(envelopesResponse.body)
      val href = (parsedBody \ "files" \\ "href").head

      When("Retrieve File Details")
      val fileResponse = getFileMetadataFor(envelopeId,fileId)

      Then("Receive 200")
      fileResponse.status shouldBe OK
    }

    scenario("Upload and Download File") {
      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}" + "%2c")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      val storeFile = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data.getBytes)))

      When("I call GET /file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK

      And("Routing request was submitted")
      submitRoutingRequest(envelopeId, "TEST")

      And("Download Zip")
      downloadEnvelope(envelopeId).status shouldBe OK
    }

    scenario("Upload and Download Zip") {
      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}" + "%2c")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data.getBytes)))


      And("Routing request was submitted")
      submitRoutingRequest(envelopeId, "TEST")

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

        And("response body should include file content")
        response.body.contains(data) shouldBe true
      }
    }

    scenario("Delete an existing file with fileId containing %2c") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId() + "%2c"}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("File is registered as stored")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 0))

      When(s"I invoke DELETE envelope/$envelopeId/files/$fileId")
      val response: WSResponse = delete(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }

}
