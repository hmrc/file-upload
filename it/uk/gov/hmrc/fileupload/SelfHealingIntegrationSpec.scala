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
