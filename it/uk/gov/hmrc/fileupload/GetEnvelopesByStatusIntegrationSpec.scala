/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

class GetEnvelopesByStatusIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  def countSubstring(str: String, substr: String) = substr.r.findAllMatchIn(str).length

  Feature("GetEnvelopesByStatus") {

    Scenario("List Envelopes for a given status with inclusive true") {
      Given("A list of status")
      val status = List("OPEN", "CLOSED")

      And("There exist one envelope with status CLOSED")
      val id = createEnvelope()
      submitRoutingRequest(id, "TEST")
      Thread.sleep(1000)

      And("There exist two envelope with status OPEN")
      createEnvelope()
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status, inclusive = true)
        response.status shouldBe OK
      }

      When(s"I invoke GET /file-upload/envelopes?status=OPEN&status=CLOSED&inclusive=true")
      val response = getEnvelopesForStatus(status, inclusive = true)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      val result = response.body
      countSubstring(result, "OPEN") shouldBe 2
      countSubstring(result, "CLOSED") shouldBe 1
    }

    Scenario("List Envelopes for a given status with inclusive false") {
      Given("A list of status")
      val status = List("OPEN")

      And("There exist one envelope with status CLOSED")
      submitRoutingRequest(createEnvelope(), "TEST")
      Thread.sleep(1000)

      And("There exist two envelope with status OPEN")
      createEnvelope()
      createEnvelope()

      eventually {
        val response = getEnvelopesForStatus(status, inclusive = false)
        response.status shouldBe OK
      }

      When(s"I invoke GET /file-upload/envelopes?status=OPEN&inclusive=false")
      val response = getEnvelopesForStatus(status, inclusive = false)

      Then("I will receive a 200 Ok response")
      response.status shouldBe OK

      val result = response.body
      countSubstring(result, "OPEN") shouldBe 0
      countSubstring(result, "CLOSED") shouldBe 1
    }
  }
}
