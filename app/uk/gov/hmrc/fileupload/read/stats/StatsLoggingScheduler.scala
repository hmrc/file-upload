/*
 * Copyright 2020 HM Revenue & Customs
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

import java.time.{Instant, Duration => JDuration}

import akka.actor.{ActorSystem, Cancellable}
import play.api.libs.json._
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object StatsLoggingScheduler {

  def initialize(actorSystem: ActorSystem,
                 configuration: StatsLoggingConfiguration,
                 statsLogger: StatsLogger)
                (implicit executionContext: ExecutionContext): Cancellable = {

    actorSystem.scheduler.schedule(configuration.initialDelay, configuration.interval) {
      statsLogger.logAddedOverTimePeriod(
        configuration.timePeriod, configuration.maximumInProgressFiles
      )
    }
  }
}

class StatsLogger(statsRepository: Repository,
                  statsLogger: StatsLogWriter)
                 (implicit executionContext: ExecutionContext) {

  def logAddedOverTimePeriod(timePeriod: Duration,
                             maximumInProgressFiles: Option[Int]): Future[Unit] = {
    countAddedOverTimePeriod(timePeriod).map { added =>
      maximumInProgressFiles match {
        case Some(maximum: Int) => checkIfAddedExceedsMaximum(added, maximum, timePeriod)
        case None => statsLogger.logRepoSize(added, timePeriod)
      }
    }
  }

  private def checkIfAddedExceedsMaximum(added: Int, maximum: Int, timePeriod: Duration): Unit = {
      if (added > maximum) statsLogger.logRepoWarning(added, maximum, timePeriod)
      else statsLogger.logRepoSize(added, timePeriod)
  }

  private def countAddedOverTimePeriod(duration: Duration): Future[Int] = {
    countAddedSince(Instant.now.minus(JDuration.ofMillis(duration.toMillis)))
  }

  private def countAddedSince(start: Instant): Future[Int] = {
    statsRepository.find("startedAt" -> Json.obj("$gt" -> start.toEpochMilli)).map { addedSinceStart =>
      addedSinceStart.size
    }
  }
}

class StatsLogWriter {
  def logRepoSize(count: Int, timePeriod: Duration): Unit =
    Logger.info(addedOverTimePeriod(count, timePeriod))

  def logRepoWarning(count: Int, maximum: Int, timePeriod: Duration): Unit =
    Logger.warn(s"Number of in progress files exceeds maximum $maximum. " + addedOverTimePeriod(count, timePeriod))

  private def addedOverTimePeriod(count: Int, timePeriod: Duration): String =
    s"Number of in progress files added is: $count over ${timePeriod.toMinutes} minutes"
}

object StatsLoggingConfiguration {

  def apply(runModeConfiguration: Configuration): StatsLoggingConfiguration = {
    val initialDelayKey = "stats.inprogressfiles.initialdelay"
    val intervalKey = "stats.inprogressfiles.interval"
    val timePeriodKey = "stats.inprogressfiles.timeperiod"
    val maximumInProgressFiles = "stats.inprogressfiles.maximum"

    StatsLoggingConfiguration(
      initialDelay = durationFromConfig(initialDelayKey, runModeConfiguration),
      interval = durationFromConfig(intervalKey, runModeConfiguration),
      timePeriod = durationFromConfig(timePeriodKey, runModeConfiguration),
      maximumInProgressFiles = runModeConfiguration.getInt(maximumInProgressFiles))
  }

  private def durationFromConfig(key: String, configuration: Configuration): FiniteDuration = {
    configuration.getMilliseconds(key)
      .map(Duration(_, MILLISECONDS))
      .getOrElse(throw new RuntimeException(s"Missing configuration value for StatsLoggingConfiguration: $key"))
  }
}

case class StatsLoggingConfiguration(initialDelay: FiniteDuration,
                                     interval: FiniteDuration,
                                     timePeriod: FiniteDuration,
                                     maximumInProgressFiles: Option[Int])
