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

package uk.gov.hmrc.fileupload.controllers.constraints

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, EnvelopeConstraintsUserSetting, Size}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration

import scala.util.Try

class EnvelopeFilesConstraintsSpec extends AnyWordSpecLike with Matchers with TestApplicationComponents {

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
        result.contains(Size(f).right.get.inBytes) shouldBe true
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
          Size(c).left.get.message shouldBe "input did not match supported size format, 'KB' and 'MB' are supported, e.g. 10MB"
      }
    }
  }

  "translateToByte " should {
    "parse a constraint in MB or KB to Long" in {
      Size("10MB").right.get.inBytes shouldBe (10 * 1024 * 1024)

      Size("100KB").right.get.inBytes shouldBe (100 * 1024)
    }
  }

  "Pass no envelope constraints to formatEnvelopeConstraints" should {
    "return default constraints, but no content type constraints" in {
      val envelopeConstraintsNone = EnvelopeConstraintsUserSetting(None,None,None,None)
      val createDefaultConstraints = EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(envelopeConstraintsNone, envelopeConstraintsConfigure)
      val expectedEnvelopeConstraints = Right(EnvelopeFilesConstraints(defaultMaxItems,defaultMaxSize,defaultMaxSizePerItem, Some(true)))
      createDefaultConstraints shouldBe expectedEnvelopeConstraints
    }
  }

  "Pass defined envelope constraints to formatEnvelopeConstraints" should {
    "return defined constraints, but no content type constraints" in {
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("30MB"),Some("8MB"),Some(List("applicaiton/pdf")), Some(false))
      val createEnvelopeWithConstraints = EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(envelopeWithConstraints, envelopeConstraintsConfigure)
      val expectedEnvelopeConstraints = Right(EnvelopeFilesConstraints(10,Size("30MB").right.get,Size("8MB").right.get, Some(false)))
      createEnvelopeWithConstraints shouldBe expectedEnvelopeConstraints
    }
  }

  "Pass envelope constraints when maxSizePerItem greater than maxSize" should {
    "return error message: constraints.maxSizePerItem can not greater than constraints.maxSize" in {
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("30MB"),Some("31MB"),Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{EnvelopeConstraintsConfiguration
                                                    .validateEnvelopeFilesConstraints(envelopeWithConstraints, envelopeConstraintsConfigure)}
                                                    .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
        "constraints.maxSizePerItem can not greater than constraints.maxSize"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }

  "Pass envelope constraints when constraints.maxSize is not a valid input" should {
    s"return error message: " +
      s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of " +
      s"${acceptedConstraints.maxSize}" in {
      val errorInput = Some("251MB")
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),errorInput,Some("30MB"),Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{EnvelopeConstraintsConfiguration
                                                    .validateEnvelopeFilesConstraints(envelopeWithConstraints, envelopeConstraintsConfigure)}
                                                    .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
        s"Input for constraints.maxSize is not a valid input, " +
        s"and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedEnvelopeConstraints.maxSize}"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }

  "Pass envelope constraints when constraints.maxSizePerItem is not a valid input" should {
    s"return error message: " +
      s"Input for constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of " +
      s"${acceptedConstraints.maxSizePerItem}" in {
      val errorInput = Some("101MB")
      val envelopeWithConstraints = EnvelopeConstraintsUserSetting(Some(10),Some("250MB"),errorInput,Some(List("applicaiton/pdf")))
      val createEnvelopeWithErrorConstraints = Try{EnvelopeConstraintsConfiguration
                                                    .validateEnvelopeFilesConstraints(envelopeWithConstraints, envelopeConstraintsConfigure)}
                                                    .failed.map(_.toString).getOrElse("")
      val expectedErrorMessage = "java.lang.IllegalArgumentException: requirement failed: " +
        s"Input constraints.maxSizePerItem is not a valid input, " +
        s"and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedEnvelopeConstraints.maxSizePerItem}"
      createEnvelopeWithErrorConstraints shouldBe expectedErrorMessage
    }
  }
}
