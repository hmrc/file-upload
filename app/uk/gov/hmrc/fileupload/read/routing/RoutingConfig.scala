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

package uk.gov.hmrc.fileupload.read.routing

import play.api.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration


case class RoutingConfig(
  initialDelay     : FiniteDuration,
  interval         : FiniteDuration,
  clientId         : String,
  recipientOrSender: String,
  pushUrl          : String,
  destinations     : List[String],
  informationType  : String,
  throttleElements : Int,
  throttlePer      : FiniteDuration,
  pushRetryBackoff : FiniteDuration
)

object RoutingConfig {

  def apply(config: Configuration): RoutingConfig = {

    def getStringList(key: String): List[String] =
      config.underlying.getStringList(key).asScala.toList

    RoutingConfig(
      initialDelay      = config.get[FiniteDuration]("routing.initialDelay"),
      interval          = config.get[FiniteDuration]("routing.interval"),
      clientId          = config.get[String]("routing.clientId"),
      recipientOrSender = config.get[String]("routing.recipientOrSender"),
      pushUrl           = config.get[String]("routing.pushUrl"),
      destinations      = getStringList("routing.destinations"),
      informationType   = config.get[String]("routing.informationType"),
      throttleElements  = config.get[Int]("routing.throttleElements"),
      throttlePer       = config.get[FiniteDuration]("routing.throttlePer"),
      pushRetryBackoff  = config.get[FiniteDuration]("routing.pushRetryBackoff")
    )
  }
}
