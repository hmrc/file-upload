package uk.gov.hmrc.fileupload

import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

/**
  * Created by paul on 26/06/17.
  */
class UrlEncodingISpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

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

      println()
      println()
      println(fileResponse.body)
      println()
      println()

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

      When("I call GET /file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK
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
      val storeFile = sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

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
        response.body.contains("sampleFileContent") shouldBe true
      }
    }
  }

}
