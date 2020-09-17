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

package uk.gov.hmrc.fileupload

import java.time.Duration

import akka.actor.{ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, TestSuite, TestData}
import org.scalatestplus.play.OneAppPerTest
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.ApplicationLoader.Context
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api._
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration

trait ApplicationComponents extends GuiceOneServerPerSuite with BeforeAndAfterAll {
  this: TestSuite =>

  // creates a new application and sets the components
  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      //.disable(classOf[com.kenshoo.play.metrics.PlayModule], classOf[Module])
      .configure("metrics.jvm" -> false)
      .build()

  val acceptedMaxItems: Int = 100
  val acceptedMaxSize: Size = Size("250MB").right.get //250 * 1024 * 1024
  val acceptedMaxSizePerItem: Size = Size("100MB").right.get //100 * 1024 * 1024
  val acceptedAllowZeroLengthFiles = false

  val defaultMaxItems: Int = 100
  val defaultMaxSize: Size = Size("25MB").right.get //25 * 1024 * 1024
  val defaultMaxSizePerItem: Size = Size("10MB").right.get //10 * 1024 * 1024

  val defaultConstraints =
    EnvelopeFilesConstraints(maxItems = defaultMaxItems,
      maxSize = defaultMaxSize,
      maxSizePerItem = defaultMaxSizePerItem,
      allowZeroLengthFiles = Some(true))

  val acceptedConstraints =
    EnvelopeFilesConstraints(maxItems = acceptedMaxItems,
      maxSize = acceptedMaxSize,
      maxSizePerItem = acceptedMaxSizePerItem,
      allowZeroLengthFiles = Some(false))

  val envelopeConstraintsConfigure = EnvelopeConstraintsConfiguration(
    acceptedEnvelopeConstraints = EnvelopeFilesConstraints(acceptedMaxItems, acceptedMaxSize, acceptedMaxSizePerItem, Some(false)),
    defaultEnvelopeConstraints  = EnvelopeFilesConstraints(defaultMaxItems, defaultMaxSize, defaultMaxSizePerItem, Some(true)),
    Duration.parse("PT4H"),
    Duration.parse("PT1H"),
    true)
}

/*
class TestApplicationModule(context: Context) extends ApplicationModule(context = context) {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq()
}
*/
