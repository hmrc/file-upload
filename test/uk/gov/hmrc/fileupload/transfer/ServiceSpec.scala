/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.transfer

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.envelope.Service.{FindEnvelopeNotFoundError, FindServiceError}
import uk.gov.hmrc.fileupload.envelope.{Envelope, EnvelopeStatusDeleted, EnvelopeStatusOpen}
import uk.gov.hmrc.fileupload.transfer.Service.{SoftDeleteEnvelopeAlreadyDeleted, SoftDeleteEnvelopeInWrongState, SoftDeleteEnvelopeNotFound, SoftDeleteServiceError}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "softDelete" should {
    "be successful" in {
      val envelope = Envelope()

      def softDelete = Service.softDelete(_ => Future.successful(true), _ => Future.successful(Xor.Right(envelope))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Right(envelope._id)
    }

    "be already deleted" in {
      val envelope = Envelope().copy(status = EnvelopeStatusDeleted)

      def softDelete = Service.softDelete(_ => Future.successful(false), _ => Future.successful(Xor.Right(envelope))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteEnvelopeAlreadyDeleted)
    }

    "be in wrong state" in {
      val envelope = Envelope().copy(status = EnvelopeStatusOpen)

      def softDelete = Service.softDelete(_ => Future.successful(false), _ => Future.successful(Xor.Right(envelope))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteEnvelopeInWrongState)
    }

    "be not found" in {
      val envelope = Envelope()

      def softDelete = Service.softDelete(_ => Future.successful(false), _ => Future.successful(Xor.Left(FindEnvelopeNotFoundError))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteEnvelopeNotFound)
    }

    "be service error based on FindServiceError" in {
      val envelope = Envelope()

      def softDelete = Service.softDelete(_ => Future.successful(false), _ => Future.successful(Xor.Left(FindServiceError("problem")))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteServiceError("problem"))
    }

    "be service error based on future error of get envelope" in {
      val envelope = Envelope()

      def softDelete = Service.softDelete(_ => Future.successful(false), _ => Future.failed(new Exception("not good"))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteServiceError("not good"))
    }

    "be service error based on future error of delete" in {
      val envelope = Envelope()

      def softDelete = Service.softDelete(_ => Future.failed(new Exception("not good")), _ => Future.failed(new Exception("no"))) _
      val result = softDelete(envelope._id).futureValue

      result shouldBe Xor.Left(SoftDeleteServiceError("not good"))
    }
  }

}
