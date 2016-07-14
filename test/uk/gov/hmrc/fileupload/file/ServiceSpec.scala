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

package uk.gov.hmrc.fileupload.file

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.envelope.Service.FindEnvelopeNotFoundError
import uk.gov.hmrc.fileupload.file.Service.{GetMetadataNotFoundError, GetMetadataServiceError, UpdateMetadataEnvelopeNotFoundError, UpdateMetadataServiceError}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "get" should {
    "be successful" in {
      val metadata = FileMetadata()
      val get = Service.getMetadata(_ => Future.successful(Some(metadata))) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.right(metadata)
    }

    "be not found" in {
      val metadata = FileMetadata()
      val get = Service.getMetadata(_ => Future.successful(None)) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.left(GetMetadataNotFoundError(metadata._id))
    }

    "be a get service error" in {
      val metadata = FileMetadata()
      val get = Service.getMetadata(_ => Future.failed(new Exception("not good"))) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.left(GetMetadataServiceError(metadata._id, "not good"))
    }
  }

  "update" should {
    "be successful" in {
      val metadata = FileMetadata()
      val envelope = Support.envelope

      val update = Service.updateMetadata(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = update(metadata).futureValue

      result shouldBe Xor.right(metadata)
    }

    "be not found" in {
      val metadata = FileMetadata()
      val envelope = Support.envelope

      val update = Service.updateMetadata(_ => Future.successful(true), _ => Future.successful(Xor.left(FindEnvelopeNotFoundError(envelope._id)))) _

      val result = update(metadata).futureValue

      result shouldBe Xor.left(UpdateMetadataEnvelopeNotFoundError(envelope._id))
    }

    "be a service error after not successful" in {
      val metadata = FileMetadata()
      val envelope = Support.envelope

      val update = Service.updateMetadata(_ => Future.successful(false), _ => Future.successful(Xor.right(envelope))) _

      val result = update(metadata).futureValue

      result shouldBe Xor.left(UpdateMetadataServiceError(metadata._id, "Update failed"))
    }

    "be a service error after exception" in {
      val metadata = FileMetadata()
      val envelope = Support.envelope

      val update = Service.updateMetadata(_ => Future.failed(new Exception("not good")), _ => Future.successful(Xor.right(envelope))) _

      val result = update(metadata).futureValue

      result shouldBe Xor.left(UpdateMetadataServiceError(metadata._id, "not good"))
    }
  }
}
