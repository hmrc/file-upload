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

import play.api.Configuration
import uk.gov.hmrc.fileupload.read.stats.StatsLoggingConfiguration
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class StatsLoggingConfigurationSpec extends UnitSpec {

  "Given a valid set of configuration values, creating logging configuration" should {
    "return the expected logging configuration" in {
      val runModeConfiguration = Configuration.from(Map(
        "stats.inprogressfiles.initialdelay" -> 500,
        "stats.inprogressfiles.interval" -> 1000)
      )
      val loggingConfiguration = StatsLoggingConfiguration(runModeConfiguration)
      loggingConfiguration shouldBe
        StatsLoggingConfiguration(Duration(500, MILLISECONDS), Duration(1000, MILLISECONDS), None)
    }
  }

  "Given a valid set of configuration values including maximum in progress files, creating logging configuration" should {
    "return the expected logging configuration" in {
      val runModeConfiguration = Configuration.from(Map(
        "stats.inprogressfiles.initialdelay" -> 500,
        "stats.inprogressfiles.interval" -> 1000,
        "stats.inprogressfiles.maximum" -> 25
      ))
      val loggingConfiguration = StatsLoggingConfiguration(runModeConfiguration)
      loggingConfiguration shouldBe
        StatsLoggingConfiguration(Duration(500, MILLISECONDS), Duration(1000, MILLISECONDS), Some(25))
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
      assert(configurationError.getMessage.contains(
        "Invalid value at 'stats.inprogressfiles.initialdelay'"))
    }
  }
}
