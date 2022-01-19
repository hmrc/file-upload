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

import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.write.envelope.{QuarantineFile, StoreFile}

/**
  * Integration tests for FILE-180
  * Delete File
  *
  */
class DeleteFileIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FileActions
     with EventsActions
     with IntegrationPatience {

  Feature("Delete file") {

    Scenario("Delete an existing file") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextUtf8String()}")

      And("I have a valid file-ref-id")
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

      And("File is registered as stored")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 0))

      When(s"I invoke DELETE envelope/$envelopeId/files/$fileId")
      val response: WSResponse = delete(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
