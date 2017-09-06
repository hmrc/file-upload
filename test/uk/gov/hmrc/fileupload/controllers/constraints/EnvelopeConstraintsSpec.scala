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

package uk.gov.hmrc.fileupload.controllers.constraints

import uk.gov.hmrc.fileupload.controllers.{CreateEnvelopeRequest, EnvelopeConstraints, EnvelopeConstraintsUserSetting}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.fileupload.read.envelope.Envelope._
import uk.gov.hmrc.fileupload.write.envelope.NotCreated.checkContentTypes
import uk.gov.hmrc.fileupload.controllers.EnvelopeConstraints.translateToByteSize
import uk.gov.hmrc.fileupload.read.envelope.Envelope

import scala.util.Try

class EnvelopeConstraintsSpec extends UnitSpec {

  "constraint format validation" should {
    "be successful for up to 4 digits followed by either KB or MB (upper case)" in {
      val validFormats =
        List(
          "1MB",
          "22KB",
          "333MB",
          "4444KB"
        )
      val result =
        List(
          1048576,
          22528,
          349175808,
          4550656
        )

      validFormats.foreach { f =>
        result.contains(translateToByteSize(f)) shouldBe true
      }
    }

    "fail for other formats" in {
      val invalidFormats =
        List(
          "MB",
          "KB",
          "0MB",
          "01KB",
          "3gb",
          "4GB",
          "12345MB"
        )

      invalidFormats.foreach { c =>
        intercept[Exception] {
          translateToByteSize(c)
        }.getMessage shouldBe "Invalid constraint input"
      }
    }
  }

  "translateToByte " should {
    "parse a constraint in MB or KB to Long" in {
      translateToByteSize("10MB") shouldBe (10 * 1024 * 1024)

      translateToByteSize("100KB") shouldBe (100 * 1024)
    }
  }

  "checkContentTypes is empty" should {
    "set the default content types" in {
      CreateEnvelopeRequest checkContentTypes Nil shouldBe defaultContentTypes
    }
  }

  "checkContentTypes is not empty and validates" should {
    "return false if content type is invalid" in {
      val wrongType = CreateEnvelopeRequest checkContentTypes List("any")
      wrongType shouldBe List("any")
      checkContentTypes(wrongType, acceptedContentTypes) shouldBe false
    }
    "return true if content type is valid" in {
      val goodType = CreateEnvelopeRequest checkContentTypes List("image/jpeg")
      goodType shouldBe List("image/jpeg")
      checkContentTypes(goodType, acceptedContentTypes) shouldBe true

    }
  }

  "Pass no envelope constraints to formatEnvelopeConstraints" should {
    "return default constraints" in {
      val envelopeConstraintsNone = EnvelopeConstraintsUserSetting(None,None,None,None)
      val createDefaultConstraints = CreateEnvelopeRequest formatUserEnvelopeConstraints envelopeConstraintsNone
      val expectedEnvelopeConstraints = Some(EnvelopeConstraints(defaultMaxItems,defaultMaxSize,defaultMaxSizePerItem,defaultContentTypes))
      createDefaultConstraints shouldBe expectedEnvelopeConstraints
    }
  }

  "Pass defined envelope constraints to formatEnvelopeConstraints" should {
    "return defined constraints" in {
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("30MB"),Some("8MB"),Some(List("applicaiton/pdf")))
      val createEnvelopeWithConstraints = CreateEnvelopeRequest formatUserEnvelopeConstraints envelopeWithConstraints
      val expectedEnvelopeConstraints = Some(EnvelopeConstraints(10,"30MB","8MB",List("applicaiton/pdf")))
      createEnvelopeWithConstraints shouldBe expectedEnvelopeConstraints
    }
  }

  "Pass envelope constraints when maxSizePerItem greater than maxSize" should {
    "return error message: constraints.maxSizePerItem can not greater than constraints.maxSize" in {
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("30MB"),Some("31MB"),Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{CreateEnvelopeRequest formatUserEnvelopeConstraints envelopeWithConstraints}
                                              .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
                                 "constraints.maxSizePerItem can not greater than constraints.maxSize"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }

  "Pass envelope constraints when constraints.maxSize is not a valid input" should {
    s"return error message: " +
      s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of " +
      s"${Envelope.acceptedConstraints.maxSize}" in {
      val errorInput = Some("yy")
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),errorInput,Some("31MB"),Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{CreateEnvelopeRequest formatUserEnvelopeConstraints envelopeWithConstraints}
        .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
                                 "Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of 250MB"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }

  "Pass envelope constraints when constraints.maxSizePerItem is not a valid input" should {
    s"return error message: " +
      s"Input for constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of " +
      s"${Envelope.acceptedConstraints.maxSizePerItem}" in {
      val errorInput = Some("yy")
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("31MB"),errorInput,Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{CreateEnvelopeRequest formatUserEnvelopeConstraints envelopeWithConstraints}
        .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
                                 "Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of 100MB"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }
}
