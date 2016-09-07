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

import uk.gov.hmrc.fileupload.EnvelopeId

import scala.collection.mutable.ArrayBuffer

trait AggregateRoot {

  private val changes: ArrayBuffer[Event] = ArrayBuffer.empty

  def id: EnvelopeId
  def version: Version = ???

  def uncommitedChanges(): List[Event] =
    changes.toList

  def markChangesAsCommited(): Unit =
    changes.clear()

  def loadsFromHistory(events: List[Event]): Unit =
    events.foreach(apply)

  def applyChange(eventData: AnyRef): Unit = {
    val event = Event(envelopeId = id,
      version = Version(1), created = Created(System.currentTimeMillis()),
      eventType = EventType(eventData.getClass.getSimpleName), eventData = eventData)
    apply(event)
    changes.append(event)
  }

  def apply(event: Event): Unit
}
