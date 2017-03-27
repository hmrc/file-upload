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

package uk.gov.hmrc.fileupload.controllers

import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.fileupload.controllers.CreateEnvelopeRequest.envelopeConstraintsReads

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

      validFormats.foreach { f =>
        CreateEnvelopeRequest.validateConstraintFormat(f) shouldBe true
      }
    }
    "fail for other formats" in {
      val invalidFormats =
        List(
          "",
          "MB",
          "KB",
          "0MB",
          "01KB",
          "1kb",
          "2mb",
          "3gb",
          "4GB",
          "12345MB"
        )
      invalidFormats.foreach { c =>
        CreateEnvelopeRequest.validateConstraintFormat(c) shouldBe false
      }
    }
  }

  "translateToByte " should {
    "parse a constraint in MB or KB to Long" in {
      CreateEnvelopeRequest translateToByteSize "10MB" shouldBe (10 * 1024 * 1024)

      CreateEnvelopeRequest translateToByteSize "100KB" shouldBe (100 * 1024)
    }
  }

  "envelopeConstraintReader " should {
    "should parse a valid envelope constraint json" in {
      val constraintJson =
        """
          {
            "maxItems": 56,
            "maxSize": "12KB",
            "maxSizePerItem": "8KB"
          }
          """
      val expectedConstraint = EnvelopeConstraints(56, 12 * 1024, 8 * 1024)

      val parsedConstraint = Json.parse(constraintJson).validate
      parsedConstraint.get shouldBe expectedConstraint
    }
  }

  "envelopeConstraintReader " should {
    "should parse empty json to default constraint values" in {
      val constraintJson =
        """{}"""
      val expectedConstraint = EnvelopeConstraints(100, 25 * 1024 * 1024, 10 * 1024 * 1024)

      val parsedConstraint = Json.parse(constraintJson).validate
      parsedConstraint.get shouldBe expectedConstraint
    }
  }

  "envelopeConstraintReader " should {
    "should fail the parsing when invalid constraint value is passed" in {
      val constraintJson =
        """
          {
            "maxItems": 56,
            "maxSize": "12GB",
            "maxSizePerItem": "8KB"
          }
          """

      val parsedConstraint = Json.parse(constraintJson).validate
      parsedConstraint.isError shouldBe true
      parsedConstraint.toString.contains("Unable to parse") shouldBe true
    }
  }

}
