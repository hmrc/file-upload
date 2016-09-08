package uk.gov.hmrc.fileupload.write.envelope

import uk.gov.hmrc.fileupload.domain.EventStore

object CommandHandler {

  def handleCommand(command: EnvelopeCommand)
                   (implicit eventStore: EventStore, publish: AnyRef => Unit): Unit = {
    val aggregate = new Envelope()(eventStore, publish)
    aggregate.handleCommand(command)
  }

}
