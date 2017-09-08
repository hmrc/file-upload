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

import org.scalatest.{BeforeAndAfterAll, Suite, TestData}
import org.scalatestplus.play.OneAppPerTest
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.fileupload.controllers.EnvelopeConstraints
import uk.gov.hmrc.fileupload.read.envelope.EnvelopeConstraintsConfigure
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes

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
  val acceptedMaxSize: String = "250MB" //250 * 1024 * 1024
  val acceptedMaxSizePerItem: String = "100MB" //100 * 1024 * 1024
  val acceptedContentTypes: List[ContentTypes] =
    List("application/pdf",
      "image/jpeg",
      "text/xml",
      "text/csv",
      "application/xml",
      "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

  val defaultMaxItems: Int = 100
  val defaultMaxSize: String = "25MB" //25 * 1024 * 1024
  val defaultMaxSizePerItem: String = "10MB" //10 * 1024 * 1024
  val defaultContentTypes: List[ContentTypes] = List("application/pdf","image/jpeg","application/xml","text/xml")

  val defaultConstraints =
    EnvelopeConstraints(maxItems = defaultMaxItems,
      maxSize = defaultMaxSize,
      maxSizePerItem = defaultMaxSizePerItem,
      contentTypes = defaultContentTypes)

  val acceptedConstraints =
    EnvelopeConstraints(maxItems = acceptedMaxItems,
      maxSize = acceptedMaxSize,
      maxSizePerItem = acceptedMaxSizePerItem,
      contentTypes = acceptedContentTypes)

  val envelopeConstraintsConfigure = EnvelopeConstraintsConfigure(acceptedMaxItems = acceptedMaxItems,
                                                                  acceptedMaxSize = acceptedMaxSize,
                                                                  acceptedMaxSizePerItem = acceptedMaxSizePerItem,
                                                                  acceptedContentTypes = acceptedContentTypes,
                                                                  defaultMaxItems = defaultMaxItems,
                                                                  defaultMaxSize = defaultMaxSize,
                                                                  defaultMaxSizePerItem = defaultMaxSizePerItem,
                                                                  defaultContentTypes = defaultContentTypes)
}

class TestApplicationModule(context: Context) extends ApplicationModule(context = context) {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq()
}
