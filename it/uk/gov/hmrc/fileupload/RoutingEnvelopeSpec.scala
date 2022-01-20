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

import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope._

class RoutingEnvelopeSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {


  Feature("Routing Envelope Spec") {

    Scenario("Route envelope which exceeds maxItems allowed") {
      Given("I have a valid envelope with maxItems allowed = 1")
      stubCallback()

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBodyWithLowConstraints(Map("callbackUrl" -> callbackUrl())))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))

      And("I have two valid files are going to uploaded")
      val fileOneId = FileId(s"fileId-${nextUtf8String()}")
      val fileTwoId = FileId(s"fileId-${nextUtf8String()}")

      And("I have two valid file-ref-id")
      val fileOneRefId = FileRefId(s"fileRefId-${nextId()}")
      val fileTwoRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileOneId, fileOneRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileTwoId, fileTwoRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileOneId, fileOneRefId))
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileTwoId, fileTwoRefId))

      When(s"StoreFile($envelopeId, $fileOneId, $fileOneRefId, 123L) command is sent")
      val storeFileOneResponse: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileOneId, fileOneRefId, 123L))

      When(s"StoreFile($envelopeId, $fileTwoId, $fileTwoRefId, 123L) command is sent")
      val storeFileTwoResponse: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileTwoId, fileTwoRefId, 123L))

      Then("I will receive a 200 OK response for store files")
      storeFileOneResponse.status shouldBe OK
      storeFileTwoResponse.status shouldBe OK

      When("I call GET /file-upload/envelopes/:envelope-id")
      val envelopeResponse = getEnvelopeFor(envelopeId)

      Then("I will receive a 200 Ok response")

      envelopeResponse.status shouldBe OK

      When("a submit routing request for that envelope is issued")
      val routingEnvelopeResponse: WSResponse = submitRoutingRequest(envelopeId, "DMS")

      Then("a Bad Request response is received indicating maxItems exceeded.")
      routingEnvelopeResponse.status shouldBe BAD_REQUEST
      routingEnvelopeResponse.body shouldBe """{"error":{"msg":"Envelope item count exceeds maximum of 1, actual: 2"}}"""
    }

    Scenario("Add two big files to envelope (valid)") {
      Given("Route envelope which exceeds maxSize allowed")
      stubCallback()

      val createEnvelopeResponse = createEnvelope(
        EnvelopeReportSupport.requestBodyWithLowConstraints(
          Map("callbackUrl" -> callbackUrl(), "maxItems" -> 2)
        )
      )
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))

      And("I have two valid file-id")
      val fileOneId = FileId(s"fileId-${nextId()}")
      val fileTwoId = FileId(s"fileId-${nextId()}")

      And("I have two valid file-ref-id")
      val fileOneRefId = FileRefId(s"fileRefId-${nextId()}")
      val fileTwoRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileOneId, fileOneRefId, 0, "test.pdf", "pdf", Some(1024 * 1024), Json.obj()))
      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileTwoId, fileTwoRefId, 0, "test.pdf", "pdf", Some(1024 * 1024), Json.obj()))

      And("FileScanned")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileOneId, fileOneRefId))
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileTwoId, fileTwoRefId))

      When(s"StoreFile($envelopeId, $fileOneId, $fileOneRefId, 123L) command is sent")
      val storeFileOneResponse: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileOneId, fileOneRefId, 1024 * 1024))

      When(s"StoreFile($envelopeId, $fileTwoId, $fileOneRefId, 123L) command is sent")
      val storeFileTwoResponse: WSResponse = sendCommandStoreFile(StoreFile(envelopeId, fileTwoId, fileTwoRefId, 1024 * 1024))

      Then("I will receive a 200 OK response for store files")
      storeFileOneResponse.status shouldBe OK
      storeFileTwoResponse.status shouldBe OK

      When("a submit routing request for that envelope is issued")
      val routingEnvelopeResponse: WSResponse = submitRoutingRequest(envelopeId, "DMS")

      Then("a Bad Request response is received indicating maxSize exceeded.")
      routingEnvelopeResponse.status shouldBe BAD_REQUEST
      routingEnvelopeResponse.body shouldBe """{"error":{"msg":"Envelope size exceeds maximum of 1.00 MB"}}"""
    }
  }
}
