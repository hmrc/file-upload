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

package uk.gov.hmrc.fileupload.support

import uk.gov.hmrc.fileupload.AllEventsPublisher
import uk.gov.hmrc.fileupload.read.infrastructure.ReportHandler
import uk.gov.hmrc.fileupload.write.infrastructure.Event

trait ControlledAllEventsPublisher extends AllEventsPublisher {

  val shouldPublish: LazyList[Boolean]

  private lazy val shouldPublishIterator = shouldPublish.iterator

  abstract override def publish(
    reportHandler: ReportHandler[_, _],
    replay       : Boolean
  ): Seq[Event] => Unit =
    if shouldPublishIterator.next() then
      super.publish(reportHandler, replay)
    else
      _ => ()
}
