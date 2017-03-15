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
import uk.gov.hmrc.fileupload.controllers.{CreateEnvelopeRequest, EnvelopeConstraints}
import uk.gov.hmrc.fileupload.{FileId, FileRefId}
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindServiceError}
import uk.gov.hmrc.fileupload.write.envelope.NotCreated
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  val defaultMaxNumFiles: Int = 100
  val defaultMaxSize: Long = 1024 * 1024 * 25
  val defaultSizePerItem: Long = 1024 * 1024 * 10

  val defaultConstraints = EnvelopeConstraints(defaultMaxNumFiles, defaultMaxSize, defaultSizePerItem)

  "find" should {
    "be successful" in {
      val envelope = Envelope(constraints = defaultConstraints)
      val find = Service.find(_ => Future.successful(Some(envelope))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not found" in {
      val envelope = Envelope(constraints = defaultConstraints)
      val find = Service.find(_ => Future.successful(None)) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindEnvelopeNotFoundError)
    }

    "be a find service error" in {
      val envelope = Envelope(constraints = defaultConstraints)
      val find = Service.find(_ => Future.failed(new Exception("not good"))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindServiceError("not good"))
    }
  }

  "findMetadata" should {
    "be successful" in {
      val file = File(FileId(), FileRefId(), FileStatusQuarantined)
      val envelope = Envelope(files = Some(List(file)), constraints = defaultConstraints)
      val findMetadata = Service.findMetadata(_ => Future.successful(Xor.right(envelope))) _

      val result = findMetadata(envelope._id, file.fileId).futureValue

      result shouldBe Xor.Right(file)
    }
  }

  "regex for envelope size input" should {
    "be true if enter is a valid size between 1kb to 25mb" in {
      val trueResultOne = 1024*1024*20
      val trueResultTwo = 1024*20
      NotCreated.isValidSize(trueResultOne, defaultMaxSize) shouldBe true
      NotCreated.isValidSize(trueResultTwo, defaultMaxSize) shouldBe true
    }
    "be false if enter is an invalid size no between 1kb to 25mb" in {
      val falseResultOne = 1024*1024*26
      val falseResultTwo = 0
      NotCreated.isValidSize(falseResultOne, defaultMaxSize) shouldBe false
      NotCreated.isValidSize(falseResultTwo, defaultMaxSize) shouldBe false
    }
  }

  "regex for change size from string to bytes Long" should {
    "return -1 if input is invalid, otherwise return a long number" in {
      CreateEnvelopeRequest.sizeToByte("26mb") shouldBe Some(1024*1024*26)
      CreateEnvelopeRequest.sizeToByte("26kb") shouldBe Some(1024*26)
      CreateEnvelopeRequest.sizeToByte("0kb") shouldBe None
      CreateEnvelopeRequest.sizeToByte("kb") shouldBe None
      CreateEnvelopeRequest.sizeToByte("foo") shouldBe None
    }
  }
}
