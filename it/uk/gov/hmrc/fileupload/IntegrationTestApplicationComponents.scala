/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload

import play.api.inject.bind
import org.scalatest.TestSuite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api._
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.fileupload.support.ControlledAllEventsPublisher
import uk.gov.hmrc.mongo.test.MongoSupport

trait IntegrationTestApplicationComponents extends GuiceOneServerPerSuite with MongoSupport {
  this: TestSuite =>

  lazy val pushUrl: Option[String] = None
  lazy val pushDestinations: Option[List[String]] = None

  val conf =
    Seq(
      "metrics.jvm" -> false,
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
      "auditing.enabled" -> "false",
      "feature.basicAuthEnabled" -> "true",
      "constraints.enforceHttps" -> "false",
      "routing.initialDelay" -> "1.second",
      "routing.interval" -> "1.second",
      "routing.clientId" -> "123",
      "microservice.services.file-upload-frontend.host" -> "localhost",
      "microservice.services.file-upload-frontend.port" -> "8017"
    ) ++
    pushUrl.fold(Map.empty[String, String])(url => Map("routing.pushUrl" -> url)) ++
    pushDestinations.fold(Map.empty[String, String])(_.zipWithIndex.map { case (destination, i) => s"routing.destinations.$i" -> destination }.toMap)

  val allEventsPublishControl: Stream[Boolean] = Stream.continually(true)

  // creates a new application and sets the components
  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(conf: _*)
      .overrides(
        bind[AllEventsPublisher].to(new DefaultAllEventsPublisher with ControlledAllEventsPublisher {
          override val shouldPublish: Stream[Boolean] = allEventsPublishControl
        })
      )
      .build()
}
