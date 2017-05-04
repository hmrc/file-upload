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
import uk.gov.hmrc.fileupload.read.envelope.Envelope.ContentTypes
import uk.gov.hmrc.play.test.UnitSpec

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
        result.contains(CreateEnvelopeRequest.translateToByteSize(f)) shouldBe true
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
          "3gb",
          "4GB",
          "12345MB"
        )

      invalidFormats.foreach { c =>
        intercept[Exception] {
          CreateEnvelopeRequest.translateToByteSize(c)
        }.getMessage shouldBe s"Invalid constraint input"
      }
    }
  }

  "translateToByte " should {
    "parse a constraint in MB or KB to Long" in {
      CreateEnvelopeRequest translateToByteSize "10MB" shouldBe (10 * 1024 * 1024)

      CreateEnvelopeRequest translateToByteSize "100KB" shouldBe (100 * 1024)
    }
  }
}
