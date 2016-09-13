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

import java.util.UUID

import cats.data.Xor

import scala.concurrent.Future

trait AggregateRoot[C <: Command, S] {

  def version: Version = ???

  def defaultState: () => S

  def eventStore: EventStore

  def publish: AnyRef => Unit

  def createUnitOfWork(streamId: StreamId, eventsData: List[EventData], version: Version) = {
    val created = Created(System.currentTimeMillis())

    UnitOfWork(streamId = streamId, version = version, created = created, events = eventsData.map { eventData =>
      Event(
        eventId = EventId(UUID.randomUUID().toString),
        streamId = streamId,
        version = version,
        created = created,
        eventType = EventType(eventData.getClass.getName),
        eventData = eventData)
    })
  }

  def createEvent(eventData: EventData, streamId: StreamId) =
    Event(eventId = EventId(UUID.randomUUID().toString),
      streamId = streamId, version = Version(1), created = Created(System.currentTimeMillis()),
      eventType = EventType(eventData.getClass.getName), eventData = eventData)

  def apply: PartialFunction[(S, EventData), S]

  def applyEvent(state: S, event: EventData): S =
    apply.applyOrElse((state, event), (input: (S, EventData)) => state)

  def handle: PartialFunction[(C, S), Xor[String, List[EventData]]]

  def handleCommand(command: C): Future[Boolean] = {
    println(s"Handle Command $command")
    val historicalUnitsOfWork = eventStore.unitsOfWorkForAggregate(command.streamId)
    val lastVersion = historicalUnitsOfWork.reverse.headOption.map(_.version).getOrElse(Version(0))
    val historicalEvents = historicalUnitsOfWork.flatMap(_.events)
    println(s"historicalEvents $historicalEvents")

    val currentState = historicalEvents.foldLeft(defaultState()) { (state, event) =>
      applyEvent(state, event.eventData)
    }

    val xorEventsData = handle.applyOrElse((command, currentState), (input: (C, S)) => Xor.Left("no handler"))

    xorEventsData match {
      case Xor.Left(_) => Future.successful(false)
      case Xor.Right(eventsData) =>
        val unitOfWork = createUnitOfWork(command.streamId, eventsData, lastVersion.nextVersion())

        eventStore.saveUnitOfWork(command.streamId, unitOfWork)
        println(s"events saved $unitOfWork")
        unitOfWork.events.foreach(publish)
        Future.successful(true)
    }
  }
}

object AggregateRoot {

  implicit def EventDataToXorRight(event: EventData): Xor[String, List[EventData]] =
    Xor.Right(List(event))

  implicit def EventsDataToXorRight(events: List[EventData]): Xor[String, List[EventData]] =
    Xor.Right(events)

  implicit def StringToXorLeft(reason: String): Xor[String, List[EventData]] =
    Xor.Left(reason)
}
