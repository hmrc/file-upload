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

package uk.gov.hmrc.fileupload.read.envelope.stats

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.fileupload.read.stats.StatsLoggingConfiguration

import scala.concurrent.duration.DurationInt

class StatsLoggingConfigurationSpec extends AnyWordSpecLike with Matchers {

  "Given a valid set of configuration values, creating logging configuration" should {
    "return the expected logging configuration" in {
      val runModeConfiguration = Configuration.from(Map(
        "stats.inprogressfiles.initialdelay" -> "500 milliseconds",
        "stats.inprogressfiles.interval" -> "1 second",
        "stats.inprogressfiles.timeperiod" -> "1 day"
      ))
      val loggingConfiguration = StatsLoggingConfiguration(runModeConfiguration)
      loggingConfiguration shouldBe
        StatsLoggingConfiguration(500.millis, 1000.millis, 1.day, None)
    }
  }

  "Given a valid set of configuration values including maximum in progress files, creating logging configuration" should {
    "return the expected logging configuration" in {
      val runModeConfiguration = Configuration.from(Map(
        "stats.inprogressfiles.initialdelay" -> "500 milliseconds",
        "stats.inprogressfiles.interval" -> "1 second",
        "stats.inprogressfiles.timeperiod" -> "1 days",
        "stats.inprogressfiles.maximum" -> 25
      ))
      val loggingConfiguration = StatsLoggingConfiguration(runModeConfiguration)
      loggingConfiguration shouldBe
        StatsLoggingConfiguration(500.millis, 1000.millis, 1.day, Some(25))
    }
  }

  "Given incomplete set of configuration values, creating logging configuration" should {
    "throw a RuntimeException detailing error" in {
      val runModeConfiguration = Configuration.from(Map())
      val configurationError = intercept[RuntimeException] {
          StatsLoggingConfiguration(runModeConfiguration)
        }
      assert(configurationError.getMessage.contains(
        "Missing configuration value for StatsLoggingConfiguration: stats.inprogressfiles.initialdelay"))
    }
  }

  "Given configuration values with invalid data, creating logging configuration" should {
    "throw a RuntimeException detailing error" in {
      val runModeConfiguration = Configuration.from(Map(
        "stats.inprogressfiles.initialdelay" -> "foo",
        "stats.inprogressfiles.interval" -> "bar"
      ))
      val configurationError = intercept[RuntimeException] {
        StatsLoggingConfiguration(runModeConfiguration)
      }
      assert(configurationError.getMessage.contains("Invalid value at 'stats.inprogressfiles.initialdelay"))
    }
  }
}
