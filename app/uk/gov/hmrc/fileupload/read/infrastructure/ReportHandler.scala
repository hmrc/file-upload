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

package uk.gov.hmrc.fileupload.read.infrastructure

import play.api.Logger
import uk.gov.hmrc.fileupload.read.envelope.Repository._
import uk.gov.hmrc.fileupload.write.infrastructure._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait ReportHandler[T, Id]:

  private val logger = Logger(getClass)

  def toId         : StreamId     => Id
  def update       : (T, Boolean) => Future[UpdateResult]
  def delete       : Id           => Future[DeleteResult]
  def defaultState : Id           => T
  def updateVersion: (Version, T) => T

  given ec: ExecutionContext

  def handle(replay: Boolean = false)(events: Seq[Event]): Unit =
    var eventVersion = ReportHandler.defaultVersion
    var created      = ReportHandler.defaultCreated

    events.headOption.foreach: event =>
      val id           = toId(event.streamId)
      var currentState = Option(defaultState(id))

      events.foreach: e =>
        eventVersion = e.version
        created      = e.created
        currentState = currentState.flatMap(s => apply(s -> e.eventData))

      currentState match
        case Some(entity) =>
          val updatedVersion = updateVersion(eventVersion, entity)
          update(updatedVersion, !replay)
            .onComplete:
              case Success(result) =>
                result match
                  case Right(_) =>
                    logger.info(s"Report successfully updated $updatedVersion")
                  case Left(NewerVersionAvailable) =>
                    logger.info(s"Report not stored: NewerVersionAvailable for $updatedVersion")
                  case Left(NotUpdatedError(m)) =>
                    logger.info(s"Report not stored: NoUpdatedError $m for $updatedVersion")
              case Failure(f) =>
                logger.info(s"Report not stored: ${f.getMessage} for $updatedVersion")
        case None =>
          remove(id)

  private def remove(id: Id): Unit =
    delete(id).onComplete:
      case Success(result) =>
        result match
          case Right(_) =>
            logger.info(s"Report successfully deleted $id")
          case Left(DeleteError(m)) =>
            logger.info(s"Report not deleted: $m for $id")
      case Failure(f) =>
        logger.info(s"Report not deleted: ${f.getMessage} for $id")

  def apply: PartialFunction[(T, EventData), Option[T]]

end ReportHandler

object ReportHandler {

  val defaultVersion = Version(0)
  val defaultCreated = Created(0)
}
