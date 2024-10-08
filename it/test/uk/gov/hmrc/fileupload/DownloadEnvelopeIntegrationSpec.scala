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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, QuarantineFile, StoreFile}


class DownloadEnvelopeIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FileActions
     with EventsActions
     with FakeFrontendService
     with IntegrationPatience {

  val uid = "GDaUeyIiOYoFALm.fMwt4NBMEAAn3diu"

  Feature("Download Envelope with files") {

    Scenario("A client can download an envelope including its file ~containing random UTF-8 string") {
      val zipContent = "sampleFileContent"

      val uidRegexPattern = "[a-z0-9-]*"
      mockFEServer.stubFor(
        WireMock.post(urlPathMatching(s"/internal-file-upload/zip/envelopes/($uidRegexPattern)"))
          .willReturn(WireMock.aResponse().withStatus(200).withBody(
            s"""{
              "name"       : "name.zip",
              "size"       : ${zipContent.getBytes.length},
              "md5Checksum": "ABC",
              "url"        : "http://localhost:$mockFEServicePort/aws/download"
              }
            """
          ))
      )

      mockFEServer.stubFor(
        WireMock.get(urlPathMatching(s"/aws/download"))
          .willReturn(WireMock.aResponse().withStatus(200).withBody(zipContent.getBytes))
      )

      Given("I have an envelope with files")
      val envelopeId = createEnvelope()

      val fileId = FileId(nextId())
      val fileRefId = FileRefId(uid)

      And("File has been stored in quarantine on the front-end")
      sendCommandQuarantineFile(
        QuarantineFile(
          envelopeId,
          fileId,
          fileRefId,
          0,
          FileName("file-name"),
          "contentType",
          Some(123L),
          Json.obj("metadata" -> "foo")
        )
      )

      And("File was scanned and no virus was found")
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

      And("File was stored in transient")
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 123L))

      And("Routing request was submitted")
      submitRoutingRequest(envelopeId, "TEST")

      eventually {
        val response: WSResponse = downloadEnvelope(envelopeId)
        withClue(response.body) {
          response.status shouldBe OK
        }
      }

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
      response.body.contains(zipContent) shouldBe true
    }
  }
}
