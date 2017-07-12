package uk.gov.hmrc.fileupload

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json._
import play.api.libs.ws.WSResponse
import play.utils.UriEncoding
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

// supplementary suit
class UrlEncodingISpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions with FakeFrontendService{

  val data = "{'name':'test'}"

  feature("Odd Url Encoding for FileId") {
    scenario("Get Envelope Details with a file and check if href encodes FileId") {

      Given("I have a valid envelope")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()+"%2C"}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("File is In Quarantine Store")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(data.getBytes().length), Json.obj()))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

      eventually {
        val envelopeResponse = getEnvelopeFor(envelopeId)
        envelopeResponse.status shouldBe OK
      }

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I will receive a 200 Ok response")
      envelopeResponse.status shouldBe OK

      And("the response body should contain the envelope details")
      val body: String = envelopeResponse.body
      body shouldNot be(null)

      val parsedBody: JsValue = Json.parse(body)

      val href = (parsedBody \ "files" \\ "href").head.toString()

      val encodedFileId = urlEncode(fileId)
      encodedFileId.contains("%252C") shouldBe true

      val targetUrl = s"/file-upload/envelopes/$envelopeId/files/$encodedFileId/content"

      href shouldBe ("\""+targetUrl+"\"")
    }
  }

}
