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

package uk.gov.hmrc.fileupload

import java.time.Duration

import org.scalatest.{BeforeAndAfterAll, EitherValues, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration

trait TestApplicationComponents
  extends GuiceOneServerPerSuite
     with BeforeAndAfterAll
     with EitherValues {
  this: TestSuite =>

  // creates a new application and sets the components
  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .build()

  val acceptedMaxItems: Int = 100
  val acceptedMaxSize: Size = Size("250MB").value //250 * 1024 * 1024
  val acceptedMaxSizePerItem: Size = Size("100MB").value //100 * 1024 * 1024
  val acceptedAllowZeroLengthFiles = false

  val defaultMaxItems: Int = 100
  val defaultMaxSize: Size = Size("25MB").value //25 * 1024 * 1024
  val defaultMaxSizePerItem: Size = Size("10MB").value //10 * 1024 * 1024

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
