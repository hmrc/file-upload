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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.fileupload.controllers.routing.FileReceived
import uk.gov.hmrc.fileupload.read.routing.{Algorithm, Audit, Checksum, DownloadUrl, FileTransferFile, FileTransferNotification, Property, RoutingRepository, ZipData}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FakeFrontendService, FakePushService, IntegrationSpec}

class FileTransferIntegrationSpec
  extends IntegrationSpec
     with EnvelopeActions
     with FakePushService
     with FakeFrontendService
     with IntegrationPatience {

  override lazy val pushUrl = Some(pushServiceUrl)
  override lazy val pushDestinations = Some(List("DMS"))

  def countSubstring(str: String, substr: String) =
    substr.r.findAllMatchIn(str).length

  Feature("File Transfer list") {
    Scenario("List Envelopes for a given destination") {
      Given("I use a destination not configured for push")
      val destination = "NO-PUSH"

      And("There exist CLOSED envelopes that match it")
      submitRoutingRequest(createEnvelope(), destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
      }

      When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain expected number of envelopes")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 1
    }

    Scenario("List Envelopes without specifying destination") {
      Given("There exist CLOSED envelopes in the DB")
      val destination = "NO-PUSH"

      val expectedNumberOfEnvelopes = 2
      (1 to expectedNumberOfEnvelopes).foreach { _ =>
        submitRoutingRequest(createEnvelope(), destination)
      }

      And("There exist envelopes with other statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
        countSubstring(response.body, "CLOSED") shouldBe 2
      }

      When(s"I invoke GET /file-transfer/envelopes (without passing destination")
      val response = getEnvelopesForDestination(None)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain all envelopes with a CLOSED status")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe expectedNumberOfEnvelopes
    }
  }

  Feature("File Transfer delete") {

    Scenario("Archive Envelope") {
      Given("I know a destination for envelopes")
      val destination = "NO-PUSH"

      And("There exist CLOSED envelopes that match it")
      val envelopeId = createEnvelope()
      submitRoutingRequest(envelopeId, destination)

      And("There exist other envelopes with different statuses")
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.status shouldBe OK
        response.body.isEmpty shouldBe false
      }

      When("I archive the envelope")
      archiveEnvelopFor(envelopeId)

      eventually {
        val response = getEnvelopesForDestination(Some(destination))
        response.status shouldBe OK
      }

      Then(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      And("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("The response will contain expected number of envelopes")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0
    }
  }

  Feature("File Transfer push") {
    Scenario("Request routing for envelopes") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      And("The push endpoint does not acknowledge")
      stubPushEndpoint(status = 500)

      And("I route an envelope")
      submitRoutingRequest(createEnvelope(), destination)

      Then("There exist ROUTE_REQUESTED envelopes that match it")
      val response = getEnvelopesForStatus(status = List("ROUTE_REQUESTED"), inclusive = true)
      response.body.isEmpty shouldBe false
    }

    Scenario("List Envelopes for a given destination") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      val envelopeId = createEnvelope()

      And("The frontend provides a download URL")
      val zipData = ZipData(
          name        = "filename",
          size        = 1L,
          md5Checksum = "4vB/MVHSuPg92a8yDf5IiA==",
          url         = DownloadUrl("http://downloadhere")
        )
      stubZipEndpoint(envelopeId, Right(zipData))

      And("The push endpoint acknowledges")
      stubPushEndpoint()

      And("I route an envelope")
      submitRoutingRequest(envelopeId, destination)

      Then("There exist ROUTE_REQUESTED envelopes that match it")
      eventually {
        val response = getEnvelopesForStatus(status = List("ROUTE_REQUESTED"), inclusive = true)
        response.body.isEmpty shouldBe false
      }

      And("The push notification was successful")
      eventually {
        verifyPushNotification(FileTransferNotification(
          informationType = "UNDEFINED",
          file            = FileTransferFile(
                              recipientOrSender = "fileUpload",
                              name              = zipData.name,
                              location          = Some(zipData.url),
                              checksum          = Checksum(Algorithm.Md5, RoutingRepository.base64ToHex(zipData.md5Checksum)),
                              size              = zipData.size.toInt,
                              properties        = List.empty[Property]
                            ),
          audit           = Audit(correlationId = envelopeId.value)
        ))
      }

      When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("It will not include the pushed envelope")
      val body = Json.parse(response.body)
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0

      When(s"The service receives a FileReceived callback")
      callCallback(FileReceived, envelopeId)

      Then("There exist CLOSED envelopes that match it")
      eventually {
        val response = getEnvelopesForStatus(status = List("CLOSED"), inclusive = true)
        response.body.isEmpty shouldBe false
      }
    }

    Scenario("Mark envelopes as routed if cannot be zipped (410)") {
      Given("I use a destination configured for push")
      val destination = "DMS"

      val envelopeId = createEnvelope()

      And("The frontend fails to create the zip with 410")
      stubZipEndpoint(envelopeId, Left(410))

      And("I route an envelope")
      submitRoutingRequest(envelopeId, destination)

      Then("There exists DELETED envelopes that match it")
      eventually {
        val response = getEnvelopesForStatus(status = List("DELETED"), inclusive = true)
        response.body.isEmpty shouldBe false
      }

      When(s"I invoke GET /file-transfer/envelopes?destination=$destination")
      val response = getEnvelopesForDestination(Some(destination))

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      And("It will not include the pushed envelope")
      val body = Json.parse(response.body)
      println(s"body=$body")
      (body \ "_embedded" \ "envelopes").as[Seq[JsValue]].size shouldBe 0
    }
  }
}
