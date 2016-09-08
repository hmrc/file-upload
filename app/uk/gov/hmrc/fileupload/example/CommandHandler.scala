package uk.gov.hmrc.fileupload.example

import uk.gov.hmrc.fileupload.domain.EventStore

object CommandHandler {

  def handleCommand(command: EnvelopeCommand)(implicit eventStore: EventStore): Unit = {
    val aggregate = new Envelope()(eventStore)
    aggregate.handleCommand(command)
  }

}
