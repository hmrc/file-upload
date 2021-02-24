package uk.gov.hmrc.fileupload.support

import uk.gov.hmrc.fileupload.AllEventsPublisher
import uk.gov.hmrc.fileupload.read.infrastructure.ReportHandler
import uk.gov.hmrc.fileupload.write.infrastructure.Event

trait ControlledAllEventsPublisher extends AllEventsPublisher {

  val shouldPublish: Stream[Boolean]

  private lazy val shouldPublishIterator = shouldPublish.iterator

  abstract override def publish(reportHandler: ReportHandler[_, _],
                                replay: Boolean): Seq[Event] => Unit =
    if (shouldPublishIterator.next()) {
      super.publish(reportHandler, replay)
    } else _ => ()
}
