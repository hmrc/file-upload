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

import play.api.libs.json.Json
import play.api.libs.ws.WSBodyReadables.readableAsString
import uk.gov.hmrc.fileupload.controllers.FileScanned
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.QuarantineFile

/**
  * Integration tests for FILE-65
  * Delete Envelope
  *
  */

class DeleteEnvelopeIntegrationSpec extends IntegrationSpec with EnvelopeActions with EventsActions {

  Feature("Delete Envelope") {
    Scenario("Delete Envelope - valid ID") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(envelopeId)

      Then("I will receive a 200 OK response")
      envelopeResponse.status shouldBe OK

      eventually {
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }

      And("the envelope should be deleted")
      val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
      checkEnvelopeDeleted.status shouldBe NOT_FOUND
    }

    Scenario("Delete Envelope - invalid ID") {
      Given("I have an invalid envelope id")
      val invalidEnvelopeId = EnvelopeId()

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(invalidEnvelopeId)


      Then("I should receive a 404 not found response")
      envelopeResponse.status shouldBe NOT_FOUND
    }

    Scenario("Delete an envelope and all files in the Envelope") {
      Given("I have a valid envelope id")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)

      And("I have all valid ids")
      val envelopeId = envelopeIdFromHeader(createResponse)
      val fileId = FileId(s"fileId-${nextId()}")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(
        envelopeId,
        fileId,
        fileRefId,
        0,
        FileName("test.pdf"),
        "pdf",
        Some(123L),
        Json.obj()
      ))

      And("File was scanned and virus was found")
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, true))

      Then("File should in the progress files list")
      eventually {
        val listShouldBe = Json.obj(
          "_id"        -> fileRefId.value,
          "envelopeId" -> envelopeId.value,
          "fileId"     -> fileId.value,
          "startedAt"  -> 0
        )
        getInProgressFiles().body shouldBe s"[$listShouldBe]"
      }

      When("I call DELETE /file-upload/envelopes/:envelope-id")
      val envelopeResponse = deleteEnvelopFor(envelopeId)

      Then("I will receive a 200 OK response")
      envelopeResponse.status shouldBe OK

      eventually {
        val checkEnvelopeDeleted = getEnvelopeFor(envelopeId)
        checkEnvelopeDeleted.status shouldBe NOT_FOUND
      }

      And("File is not in the progress files list")
      getInProgressFiles().body shouldBe "[]"
    }
  }
}
