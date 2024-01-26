/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.JsObject
import uk.gov.hmrc.fileupload.support.FileMetadataReportSupport._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.QuarantineFile

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class GetFileMetadataIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  Feature("Retrieve Metadata") {

    Scenario("GET metadata with valid envelope id") {
      Given("I have a valid envelope ID")
      val envelopeId = createEnvelope()
      val fileId = FileId(s"fileId-${nextId()}") // fixme, should be nextUtf8String, manual test passed
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      val json = (requestBodyAsJson() \ "metadata").as[JsObject]
      sendCommandQuarantineFile(QuarantineFile(
        envelopeId,
        fileId,
        fileRefId,
        0,
        FileName("test.jpg"),
        "application/pdf",
        Some(123L),
        json
      ))

      eventually {
        val response = getFileMetadataFor(envelopeId, fileId)
        response.status shouldBe OK
      }

      When(s"I invoke GET envelopes/$envelopeId/files/$fileId/metadata")
      val response = getFileMetadataFor(envelopeId, fileId)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("the response body should contain the file reference details")
      response.json shouldBe responseBodyAsJson(envelopeId, fileId)
    }

    Scenario("GET metadata with invalid envelope id") {
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
