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

trait AggregateRoot[C <: Command, S] {

  def version: Version = ???

  def defaultState: () => S

  def eventStore: EventStore

  def createEvent(eventData: EventData, streamId: StreamId) =
    Event(streamId = streamId,
      version = Version(1), created = Created(System.currentTimeMillis()),
      eventType = EventType(eventData.getClass.getName), eventData = eventData)

  def apply: PartialFunction[(S, EventData), S]

  def applyEvent(state: S, event: EventData): S =
    apply.applyOrElse((state, event), (input: (S, EventData)) => state)

  def handle: PartialFunction[(C, S), List[EventData]]

  def handleCommand(command: C): Unit = {
    println(s"Handle Command $command")
    val historicalEvents = eventStore.eventsForAggregate(command.streamId)

    val currentState = historicalEvents.foldLeft(defaultState()) { (state, event) =>
      applyEvent(state, event.eventData)
    }

    val eventsData = handle.applyOrElse((command, currentState), (input: (C, S)) => List.empty)
    val events = eventsData.map(ed => createEvent(ed, command.streamId))

    eventStore.saveEvents(command.streamId, events, Version(1))
  }
}
