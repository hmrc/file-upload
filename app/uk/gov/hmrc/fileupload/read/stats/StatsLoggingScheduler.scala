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

import java.time.{LocalDateTime, ZoneId}

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
    configuration.maximumInProgressFiles match {
      case Some(maximumFilesInProgress: Int) => statsLogger.checkAddedToday(maximumFilesInProgress)
      case None => statsLogger.logAddedToday()
    }
  }
}

class StatsLogger(statsRepository: Repository, statsLogger: StatsLogWriter)(implicit executionContext: ExecutionContext) {

  def logAddedToday(): Future[Unit] = {
    addedToday().map { added =>
      statsLogger.logRepoSize(added)
    }
  }

  def checkAddedToday(maximum: Int): Future[Unit] = {
    addedToday().map { added =>
      if (added > maximum) statsLogger.logRepoWarning(added, maximum)
      else statsLogger.logRepoSize(added)
    }
  }

  private def addedToday(): Future[Int] = {
    val startCurrentDate: Long = LocalDateTime.now()
      .minusDays(1)
      .atZone(ZoneId.of("UTC"))
      .toInstant
      .toEpochMilli

    statsRepository.find("startedAt" -> Json.obj(("$gt" -> startCurrentDate))).map { addedToday =>
      addedToday.size
    }
  }
}

class StatsLogWriter {
  def logRepoSize(count: Int): Unit = {
    Logger.debug(s"Number of in progress files added today is: $count")
  }

  def logRepoWarning(count: Int, maximum: Int): Unit = {
    Logger.warn(s"Number of in progress files is $count, maximum healthy size is: $maximum")
  }
}

object StatsLoggingConfiguration {

  def from(runModeConfiguration: Configuration): StatsLoggingConfiguration = {
    val initialDelayKey = "stats.inprogressfiles.initialdelay"
    val intervalKey = "stats.inprogressfiles.interval"
    val maximumInProgressFiles = "stats.inprogressfiles.maximum"

    val delayFromConfig: FiniteDuration = runModeConfiguration
      .getMilliseconds(initialDelayKey)
      .map(Duration(_, MILLISECONDS))
      .getOrElse(throwConfigurationException(initialDelayKey))
    val intervalFromConfig: FiniteDuration = runModeConfiguration
      .getMilliseconds(intervalKey)
      .map(Duration(_, MILLISECONDS))
      .getOrElse(throwConfigurationException(intervalKey))
    val maximumFromConfig = runModeConfiguration.getInt(maximumInProgressFiles)

    StatsLoggingConfiguration(delayFromConfig, intervalFromConfig, maximumFromConfig)
  }

  private def throwConfigurationException(key: String) = {
    throw new RuntimeException(s"Missing configuration value for StatsLoggingConfiguration: $key")
  }
}

case class StatsLoggingConfiguration(initialDelay: FiniteDuration, interval: FiniteDuration, maximumInProgressFiles: Option[Int])
