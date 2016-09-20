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

package uk.gov.hmrc.fileupload.write.infrastructure

import java.util.UUID

import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore.{NotSavedError, VersionConflictError}

import scala.concurrent.{ExecutionContext, Future}

case class Aggregate[C <: Command, S, E <: CommandNotAccepted](handler: Handler[C, S, E],
                                                               defaultState: () => S,
                                                               commonError: String => E,
                                                               publish: AnyRef => Unit,
                                                               nextEventId: () => EventId = () => EventId(UUID.randomUUID().toString),
                                                               toCreated: () => Created = () => Created(System.currentTimeMillis()))
                                                               (implicit eventStore: EventStore, executionContext: ExecutionContext) {
  type CommandResult = Xor[E, CommandAccepted.type]

  val commandAcceptedResult = Xor.Right(CommandAccepted)

  def createUnitOfWork(streamId: StreamId, eventsData: List[EventData], version: Version) = {
    val created = toCreated()

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

  def applyEvent(state: S, event: EventData): S =
    handler.on.applyOrElse((state, event), (input: (S, EventData)) => state)

  def handleCommand(command: C): Future[CommandResult] = {
    Logger.info(s"Handle Command $command")
    eventStore.unitsOfWorkForAggregate(command.streamId).flatMap {
      case Xor.Right(historicalUnitsOfWork) =>
        val lastVersion = historicalUnitsOfWork.reverse.headOption.map(_.version).getOrElse(Version(0))
        val historicalEvents = historicalUnitsOfWork.flatMap(_.events)
        Logger.info(s"historicalEvents $historicalEvents")

        val currentState = historicalEvents.foldLeft(defaultState()) { (state, event) =>
          applyEvent(state, event.eventData)
        }

        val xorEventsData: Xor[E, List[EventData]] = handler.handle.applyOrElse((command, currentState), (input: (C, S)) => Xor.Right(List.empty))

        xorEventsData match {
          case Xor.Right(eventsData) =>
            if (eventsData.nonEmpty) {
              val unitOfWork = createUnitOfWork(command.streamId, eventsData, lastVersion.nextVersion())

              eventStore.saveUnitOfWork(command.streamId, unitOfWork).map {
                case Xor.Right(_) =>
                  unitOfWork.events.foreach(publish)
                  commandAcceptedResult

                case Xor.Left(VersionConflictError) =>
                  Xor.left(commonError("version conflict"))

                case Xor.Left(NotSavedError(m)) =>
                  Xor.left(commonError(m))
              }.recover { case e => Xor.left(commonError(e.getMessage)) }

            } else {
              Future.successful(commandAcceptedResult)
            }

          case Xor.Left(e) =>
            Future.successful(Xor.left(e))
        }

      case Xor.Left(e) =>
        Future.successful(Xor.left(commonError(e.message)))
    }
  }
}
