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

package uk.gov.hmrc.fileupload.read.routing

import play.api.Configuration

import scala.concurrent.duration.{DurationLong, FiniteDuration}


case class RoutingConfig(
  initialDelay    :           FiniteDuration,
  interval        :           FiniteDuration,
  lookupPublishUrl: String => Option[String],
  host            :           String
)

object RoutingConfig {

  def apply(config: Configuration): RoutingConfig = {
    def getInt(key: String) =
      config.getInt(key).getOrElse(sys.error(s"Missing configuration: $key"))
    def getString(key: String) =
      config.getString(key).getOrElse(sys.error(s"Missing configuration: $key"))
    def getDuration(key: String) =
      config.getMilliseconds(key).getOrElse(sys.error(s"Missing configuration: $key")).millis
    RoutingConfig(
      initialDelay     = getDuration("routing.initialDelay"),
      interval         = getDuration("routing.interval"),
      lookupPublishUrl = (destination: String) => config.getString(s"routing.publishurl.$destination"),
      host             = getString("microservice.services.self.host")
    )
  }
}
