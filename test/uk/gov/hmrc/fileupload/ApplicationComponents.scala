/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterAll, Suite, TestData}
import org.scalatestplus.play.OneAppPerTest
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration

trait ApplicationComponents extends OneAppPerTest with BeforeAndAfterAll {
  this: Suite =>

  // accessed to get the components in tests
  lazy val components: ApplicationModule = new TestApplicationModule(context)

  // creates a new application and sets the components
  lazy val newApplication: Application = components.application

  lazy val context: ApplicationLoader.Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new java.io.File("."), classLoader, Mode.Test)
    ApplicationLoader.createContext(env)
  }

  override def newAppForTest(testData: TestData): Application = {
    newApplication
  }

  val acceptedMaxItems: Int = 100
  val acceptedMaxSize: Size = Size("250MB").right.get //250 * 1024 * 1024
  val acceptedMaxSizePerItem: Size = Size("100MB").right.get //100 * 1024 * 1024

  val defaultMaxItems: Int = 100
  val defaultMaxSize: Size = Size("25MB").right.get //25 * 1024 * 1024
  val defaultMaxSizePerItem: Size = Size("10MB").right.get //10 * 1024 * 1024

  val defaultConstraints =
    EnvelopeFilesConstraints(maxItems = defaultMaxItems,
      maxSize = defaultMaxSize,
      maxSizePerItem = defaultMaxSizePerItem)

  val acceptedConstraints =
    EnvelopeFilesConstraints(maxItems = acceptedMaxItems,
      maxSize = acceptedMaxSize,
      maxSizePerItem = acceptedMaxSizePerItem)

  val envelopeConstraintsConfigure = EnvelopeConstraintsConfiguration(
    acceptedEnvelopeConstraints = EnvelopeFilesConstraints(acceptedMaxItems, acceptedMaxSize, acceptedMaxSizePerItem),
    defaultEnvelopeConstraints  = EnvelopeFilesConstraints(defaultMaxItems, defaultMaxSize, defaultMaxSizePerItem),
    Duration.parse("PT4H"),
    Duration.parse("PT1H") )
}

class TestApplicationModule(context: Context) extends ApplicationModule(context = context) {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq()
}
