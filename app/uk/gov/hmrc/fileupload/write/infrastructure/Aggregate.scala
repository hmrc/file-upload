/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.Logger
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore.{NotSavedError, VersionConflictError}

import scala.concurrent.{ExecutionContext, Future}

class Aggregate[C <: Command, S](handler: Handler[C, S],
                                 defaultState: () => S,
                                 publish: AnyRef => Unit,
                                 publishAllEvents: Seq[Event] => Unit,
                                 nextEventId: () => EventId = () => EventId(UUID.randomUUID().toString),
                                 toCreated: () => Created = () => Created(System.currentTimeMillis()))
                                (implicit eventStore: EventStore, executionContext: ExecutionContext) {

  type CommandResult = Either[CommandNotAccepted, CommandAccepted.type]

  private val logger = Logger(getClass)

  val commandAcceptedResult = Right(CommandAccepted)

  val numOfRetry: Int = 15

  def createUnitOfWork(streamId: StreamId, eventsData: List[EventData], version: Version): UnitOfWork = {
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

  def applyCommand(command: C): Future[CommandResult] = {
    logger.info(s"Handle Command $command")
    eventStore.unitsOfWorkForAggregate(command.streamId).flatMap {
      case Right(historicalUnitsOfWork) =>
        val historicalEvents = historicalUnitsOfWork.flatMap(_.events)

        val (currentState, lastVersion) = historicalEvents.foldLeft((defaultState(), Aggregate.defaultVersion)) { (state, event) =>
          (applyEvent(state._1, event.eventData), event.version)
        }

        val xorEventsData: Either[CommandNotAccepted, List[EventData]] =
          handler.handle.applyOrElse((command, currentState), (input: (C, S)) => Right(List.empty))

        xorEventsData match {
          case Right(eventsData) =>
            if (eventsData.nonEmpty) {
              val nextVersion = lastVersion.nextVersion()
              val unitOfWork = createUnitOfWork(command.streamId, eventsData, nextVersion)

              eventStore.saveUnitOfWork(command.streamId, unitOfWork).map {
                case Right(newEvents) =>
                  publishAllEvents(historicalEvents ++ unitOfWork.events)
                  unitOfWork.events.foreach { event =>
                    logger.info(s"Event created $event")
                    publish(event)
                  }
                  commandAcceptedResult

                case Left(VersionConflictError) =>
                  logger.info(s"VersionConflict for version $nextVersion and $command")
                  Left(VersionConflict(nextVersion, command))

                case Left(NotSavedError(m)) =>
                  Left(CommandError(m))

                case Left(e) =>
                  Left(CommandError(e.toString))
              }.recover { case e => Left(CommandError(e.getMessage)) }

            } else {
              Future.successful(commandAcceptedResult)
            }

          case Left(e) =>
            Future.successful(Left(e))
        }

      case Left(e) =>
        Future.successful(Left(CommandError(e.message)))
    }
  }

  def handleCommand(command: C): Future[CommandResult] = {
    def run(retries: Int, command: C): Future[CommandResult] = {
      applyCommand(command).flatMap {
        case result @ Right(_) => Future.successful(result)
        case error @ Left(VersionConflict(_, _)) =>
          if (retries > 0) {
            logger.info(s"Retry $retries for $command")
            run(retries - 1, command)
          } else {
            logger.warn(s"Return with version conflict $command")
            Future.successful(error)
          }
        case error =>
          logger.warn(s"Return with error $error for $command")
          Future.successful(error)
      }
    }
    run(numOfRetry, command)
  }
}

object Aggregate {

  val defaultVersion = Version(0)
}
