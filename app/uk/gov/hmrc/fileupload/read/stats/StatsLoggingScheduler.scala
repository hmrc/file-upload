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

package uk.gov.hmrc.fileupload.read.stats

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import play.api.{Configuration, Logger}

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object StatsLoggingScheduler:

  def initialize(
    actorSystem: ActorSystem,
    configuration: StatsLoggingConfiguration,
    statsLogger: StatsLogger
  )(using
    ExecutionContext
  ): Cancellable =
    actorSystem.scheduler.scheduleAtFixedRate(configuration.initialDelay, configuration.interval)(() =>
      statsLogger.logAddedOverTimePeriod(
        configuration.timePeriod,
        configuration.maximumInProgressFiles
      )
    )

class StatsLogger(
  statsRepository: Repository,
  statsLogger: StatsLogWriter
)(using
  ExecutionContext
):

  def logAddedOverTimePeriod(
    timePeriod            : Duration,
    maximumInProgressFiles: Option[Int]
  ): Future[Unit] =
    countAddedOverTimePeriod(timePeriod)
      .map: added =>
        maximumInProgressFiles match
          case Some(maximum: Int) => checkIfAddedExceedsMaximum(added, maximum, timePeriod)
          case None               => statsLogger.logRepoSize(added, timePeriod)

  private def checkIfAddedExceedsMaximum(added: Long, maximum: Int, timePeriod: Duration): Unit =
    if added > maximum then
      statsLogger.logRepoWarning(added, maximum, timePeriod)
    else
      statsLogger.logRepoSize(added, timePeriod)

  private def countAddedOverTimePeriod(duration: Duration): Future[Long] =
    statsRepository.statsAddedSince(Instant.now().minusMillis(duration.toMillis))

end StatsLogger

class StatsLogWriter {
  private val logger = Logger(getClass)

  def logRepoSize(count: Long, timePeriod: Duration): Unit =
    logger.info(addedOverTimePeriod(count, timePeriod))

  def logRepoWarning(count: Long, maximum: Long, timePeriod: Duration): Unit =
    logger.warn(s"Number of in progress files exceeds maximum $maximum. " + addedOverTimePeriod(count, timePeriod))

  private def addedOverTimePeriod(count: Long, timePeriod: Duration): String =
    s"Number of in progress files added is: $count over ${timePeriod.toMinutes} minutes"
}

object StatsLoggingConfiguration {
  def apply(configuration: Configuration): StatsLoggingConfiguration =
    StatsLoggingConfiguration(
      initialDelay           = configuration.get[FiniteDuration]("stats.inprogressfiles.initialdelay"),
      interval               = configuration.get[FiniteDuration]("stats.inprogressfiles.interval"),
      timePeriod             = configuration.get[FiniteDuration]("stats.inprogressfiles.timeperiod"),
      maximumInProgressFiles = configuration.getOptional[Int]("stats.inprogressfiles.maximum")
    )
}

case class StatsLoggingConfiguration(
  initialDelay          : FiniteDuration,
  interval              : FiniteDuration,
  timePeriod            : FiniteDuration,
  maximumInProgressFiles: Option[Int]
)
