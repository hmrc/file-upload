package uk.gov.hmrc.fileupload

import uk.gov.hmrc.fileupload.support.{EnvelopeActions, IntegrationSpec}

class GetEnvelopesByStatusIntegrationSpec extends IntegrationSpec with EnvelopeActions {

  def countSubstring(str: String, substr: String) = substr.r.findAllMatchIn(str).length

  feature("GetEnvelopesByStatus") {

    scenario("List Envelopes for a given status with inclusive true") {
      Given("A list of status")
      val status = List("OPEN", "CLOSED")

      And("There exist one envelope with status CLOSED")
      val id = createEnvelope()
      submitRoutingRequest(id, "TEST")

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

    scenario("List Envelopes for a given status with inclusive false") {
      Given("A list of status")
      val status = List("OPEN")

      And("There exist one envelope with status CLOSED")
      submitRoutingRequest(createEnvelope(), "TEST")

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
