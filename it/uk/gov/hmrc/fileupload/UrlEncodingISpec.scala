/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload

import play.api.libs.json._
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}

// supplementary suit
class UrlEncodingISpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions with FakeFrontendService{

  val data = "{'name':'test'}"

  Feature("Odd Url Encoding for FileId") {
    Scenario("Get Envelope Details with a file and check if href encodes FileId") {

      Given("I have a valid envelope")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextUtf8String()+"%2C"}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("File is In Quarantine Store")
      sendCommandQuarantineFile(QuarantineFile(
        envelopeId,
        fileId,
        fileRefId,
        0,
        FileName("test.pdf"),
        "pdf",
        Some(data.getBytes().length),
        Json.obj()
      ))

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("I have uploaded a file")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, data.getBytes().length))

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
