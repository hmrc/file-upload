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

class Repository[T <: AggregateRoot](newInstance: (EnvelopeId) => T, storage: EventStore) {

  def save(aggregate: T, expectedVersion: Version): Unit =
    storage.saveEvents(aggregate.id, aggregate.uncommitedChanges(), expectedVersion)

  def byId(envelopeId: EnvelopeId): T = {
    val aggregate = newInstance(envelopeId)
    val events = storage.eventsForAggregate(envelopeId)
    aggregate.loadsFromHistory(events)
    aggregate
  }
}

trait EventStore {

  def saveEvents(envelopeId: EnvelopeId, events: List[Event], expectedVersion: Version)

  def eventsForAggregate(envelopeId: EnvelopeId): List[Event]
}

class InMemoryEventStore extends EventStore {

  var allEvents = Map.empty[EnvelopeId, List[Event]]

  override def saveEvents(envelopeId: EnvelopeId, events: List[Event], expectedVersion: Version): Unit = {
    println(s"saveEvent: $events")
    val currentEvents = allEvents.getOrElse(envelopeId, List.empty)
    allEvents = allEvents + (envelopeId -> currentEvents.++(events))
    //TODO: publish events after successfully saved
  }

  override def eventsForAggregate(envelopeId: EnvelopeId): List[Event] = {
    val events = allEvents.getOrElse(envelopeId, List.empty)
    println(s"eventsForAggregate: $events")
    events
  }
}
