package uk.gov.hmrc.fileupload

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

class UrlEncodingISpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions with FakeFrontendService{

  val data = "{'name':'test'}"

  feature("Odd Url Encoding for FileId") {

    scenario("Get Envelope Details with a file and check if href encodes FileId") {

      Given("I have a valid envelope")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("File is In Quarantine Store")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      eventually {
        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK
      }(PatienceConfig(timeout = Span(5,Seconds),interval = Span(5,Seconds)))

      And("the response body should contain the envelope details")
      val body: String = envelopeResponse.body
      body shouldNot be(null)

      val parsedBody: JsValue = Json.parse(body)

      val href = (parsedBody \ "files" \\ "href").head.toString()

      val actualUrl = s"/file-upload/envelopes/$envelopeId/files/${urlEncode(fileId)}/content"

      href shouldBe ("\""+actualUrl+"\"")
    }

    scenario("Get Envelope Details with a file and check if href encodes %2c") {

      Given("I have a valid envelope")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-%2c")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("File is In Quarantine Store")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      eventually {
        Then("I will receive a 200 Ok response")
        envelopeResponse.status shouldBe OK
      }(PatienceConfig(timeout = Span(5,Seconds),interval = Span(5,Seconds)))

      And("the response body should contain the envelope details")
      val body: String = envelopeResponse.body
      body shouldNot be(null)

      val parsedBody: JsValue = Json.parse(body)

      val href = (parsedBody \ "files" \\ "href").head.toString()

      val actualUrl = s"/file-upload/envelopes/$envelopeId/files/${urlEncode(fileId)}/content"

      href shouldBe ("\""+actualUrl+"\"")
    }

    scenario("Retrieve File Metadata with FileId containing random UTF-8 string") {

      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()}")
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

    scenario("Upload and Download File with FileId containing random UTF-8 string") {
      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      mockFEServer.stubFor(WireMock.get(urlPathMatching(s"/file-upload/download/envelopes/$envelopeId/files/$fileId"))
        .willReturn(WireMock.aResponse().withStatus(200).withBody(data.getBytes)))

      When("I call GET /file-upload/envelopes/:envelope-id/files/:file-id/content")
      val getFileResponse: WSResponse = download(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      getFileResponse.status shouldBe OK
    }

    scenario("Upload and Download Zip  with FileId containing random UTF-8 string") {
      Given("I have a valid envelope ")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()}")
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

      When(s"I invoke GET file-transfer/envelope/$envelopeId")
      val response: WSResponse = downloadEnvelope(envelopeId)

      eventually {
        Then("I will receive a 200 OK response")
        withClue(response.body) {
          response.status shouldBe OK
        }
      }

      And("response should include content type")
      response.header("Content-Type") shouldBe Some("application/zip")

      And("response should be chunked")
      response.header("Transfer-Encoding") shouldBe Some("chunked")

      And("response body should include file content")
      response.body.contains(data) shouldBe true
    }

    scenario("Delete an existing file with FileId containing random UTF-8 string") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextUtf8String()}")

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
