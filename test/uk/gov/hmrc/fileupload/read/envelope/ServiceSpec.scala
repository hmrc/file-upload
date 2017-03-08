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

package uk.gov.hmrc.fileupload.read.envelope

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.{FileId, FileRefId}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindServiceError}
import uk.gov.hmrc.fileupload.write.envelope.NotCreated
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "find" should {
    "be successful" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.successful(Some(envelope))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not found" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.successful(None)) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindEnvelopeNotFoundError)
    }

    "be a find service error" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.failed(new Exception("not good"))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindServiceError("not good"))
    }
  }

  "findMetadata" should {
    "be successful" in {
      val file = File(FileId(), FileRefId(), FileStatusQuarantined)
      val envelope = Envelope(files = Some(List(file)))
      val findMetadata = Service.findMetadata(_ => Future.successful(Xor.right(envelope))) _

      val result = findMetadata(envelope._id, file.fileId).futureValue

      result shouldBe Xor.Right(file)
    }
  }

  "regex for envelope size input" should {
    "be true if enter is a valid size between 1kb to 25mb" in {
      NotCreated.isValidSize("25mb", "25mb") shouldBe true
      NotCreated.isValidSize("1kb", "25mb") shouldBe true
    }
    "be false if enter is an invalid size no between 1kb to 25mb" in {
      NotCreated.isValidSize("26mb", "25mb") shouldBe false
      NotCreated.isValidSize("0kb", "25mb") shouldBe false
      NotCreated.isValidSize("kb", "25mb") shouldBe false
      NotCreated.isValidSize("foo", "25mb") shouldBe false
    }
  }

  "regex for change size from string to bytes Long" should {
    "return -1 if input is invalid, otherwise return a long number" in {
      NotCreated.sizeToByte("26mb") shouldBe 1024*1024*26
      NotCreated.sizeToByte("26kb") shouldBe 1024*26
      NotCreated.sizeToByte("0kb") shouldBe -1
      NotCreated.sizeToByte("kb") shouldBe -1
      NotCreated.sizeToByte("foo") shouldBe -1
    }
  }
}
