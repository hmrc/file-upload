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

import uk.gov.hmrc.fileupload.support.{
  EnvelopeActions,
  EventsActions,
  IntegrationSpec
}

class SelfHealingIntegrationSpec
    extends IntegrationSpec
    with EnvelopeActions
    with EventsActions {

  private val publishEventsForCreateEnvelope = true
  private val publishEventsForRoutingRequest = true
  private val dontPublishEventsForArchive = false
  private val publishAllEvents = Stream.continually(true)

  override val allEventsPublishControl = Stream(
    publishEventsForCreateEnvelope,
    publishEventsForRoutingRequest,
    dontPublishEventsForArchive
  ) ++ publishAllEvents

  Feature("Self healing") {
    Scenario("Archiving Envelope") {
      Given("There exist an envelope with routing requested")
      val createResponse = createEnvelope("{}")
      createResponse.status should equal(CREATED)
      val envelopeId = envelopeIdFromHeader(createResponse)
      eventually {
        getEnvelopeFor(envelopeId).status shouldBe OK
      }
      val routingResponse =
        submitRoutingRequest(envelopeId, destination = "NO-PUSH")
      routingResponse.status should equal(CREATED)

      When(
        "The envelope is archived but the state persistence is disabled for archiving"
      )
      val archiveResponse = archiveEnvelopFor(envelopeId)
      archiveResponse.status shouldBe OK

      Then("Eventually the envelope will attain the DELETED state on subsequent archive requests")
      eventually {
        val archiveResponse = archiveEnvelopFor(envelopeId)
        archiveResponse.status shouldBe GONE

        val checkEnvelopeStatusDeleted = getEnvelopeFor(envelopeId)
        (checkEnvelopeStatusDeleted.json \ "status")
          .as[String] shouldBe "DELETED"
      }
    }

  }

}
