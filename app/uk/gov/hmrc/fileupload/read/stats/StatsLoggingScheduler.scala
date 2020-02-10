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

import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

import akka.actor.ActorSystem
import play.api.libs.json._
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class StatsLoggingScheduler(actorSystem: ActorSystem,
                            configuration: StatsLoggingConfiguration,
                            statsLogger: StatsLogger)
                           (implicit executionContext: ExecutionContext) {

  actorSystem.scheduler.schedule(configuration.initialDelay, configuration.interval) {
    statsLogger.logAddedToday(configuration.maximumInProgressFiles)
  }
}

class StatsLogger(statsRepository: Repository,
                  statsLogger: StatsLogWriter)
                 (implicit executionContext: ExecutionContext) {

  def logAddedToday(maximumInProgressFiles: Option[Int]): Future[Unit] = {
    countAddedToday().map { added =>
      maximumInProgressFiles match {
        case Some(maximum: Int) => checkIfAddedTodayExceedsMaximum(added, maximum)
        case None => statsLogger.logRepoSize(added)
      }
    }
  }

  private def checkIfAddedTodayExceedsMaximum(added: Int, maximum: Int): Unit = {
      if (added > maximum) statsLogger.logRepoWarning(added, maximum)
      else statsLogger.logRepoSize(added)
  }

  private def countAddedToday(): Future[Int] = {
    countAddedSince(Instant.now.minus(1, DAYS))
  }

  private def countAddedSince(start: Instant): Future[Int] = {
    statsRepository.find("startedAt" -> Json.obj("$gt" -> start.toEpochMilli)).map { addedSinceStart =>
      addedSinceStart.size
    }
  }
}

class StatsLogWriter {
  def logRepoSize(count: Int): Unit = {
    Logger.info(s"Number of in progress files added today is: $count")
  }

  def logRepoWarning(count: Int, maximum: Int): Unit = {
    Logger.warn(s"Number of in progress files is $count, maximum healthy size is: $maximum")
  }
}

object StatsLoggingConfiguration {

  def apply(runModeConfiguration: Configuration): StatsLoggingConfiguration = {
    val initialDelayKey = "stats.inprogressfiles.initialdelay"
    val intervalKey = "stats.inprogressfiles.interval"
    val maximumInProgressFiles = "stats.inprogressfiles.maximum"

    val delayFromConfig: FiniteDuration = durationFromConfig(initialDelayKey, runModeConfiguration)
    val intervalFromConfig: FiniteDuration = durationFromConfig(intervalKey, runModeConfiguration)
    val maximumFromConfig = runModeConfiguration.getInt(maximumInProgressFiles)

    StatsLoggingConfiguration(delayFromConfig, intervalFromConfig, maximumFromConfig)
  }

  private def durationFromConfig(key: String, configuration: Configuration): FiniteDuration = {
    configuration
      .getMilliseconds(key)
      .map(Duration(_, MILLISECONDS))
      .getOrElse(throw new RuntimeException(s"Missing configuration value for StatsLoggingConfiguration: $key"))
  }
}

case class StatsLoggingConfiguration(initialDelay: FiniteDuration, interval: FiniteDuration, maximumInProgressFiles: Option[Int])
