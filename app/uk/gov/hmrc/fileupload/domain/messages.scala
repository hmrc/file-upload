/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.domain

case class Event(eventId: EventId, streamId: StreamId, version: Version, created: Created, eventType: EventType, eventData: EventData)

case class EventId(value: String) extends AnyVal {
  override def toString = value.toString
}

case class StreamId(value: String) extends AnyVal {
  override def toString = value.toString
}

case class Version(value: Int) extends AnyVal {
  override def toString = value.toString
}

case class Created(value: Long) extends AnyVal {
  override def toString = value.toString
}

case class EventType(value: String) extends AnyVal {
  override def toString = value
}

trait Command {
  def streamId: StreamId
}
