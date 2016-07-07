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
import uk.gov.hmrc.fileupload.file.FileService.{GetMetadataNotFoundError, GetMetadataServiceError, UpdateMetadataNotFoundError, UpdateMetadataServiceError}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class FileServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "get" should {
    "be successful" in {
      val metadata = FileMetadata()
      val get = FileService.getMetadata(_ => Future.successful(Some(metadata))) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.right(metadata)
    }

    "be not found" in {
      val metadata = FileMetadata()
      val get = FileService.getMetadata(_ => Future.successful(None)) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.left(GetMetadataNotFoundError(metadata._id))
    }

    "be a get service error" in {
      val metadata = FileMetadata()
      val get = FileService.getMetadata(_ => Future.failed(new Exception("not good"))) _

      val result = get(metadata._id).futureValue

      result shouldBe Xor.left(GetMetadataServiceError(metadata._id, "not good"))
    }
  }

  "update" should {
    "be successful" in {
      val metadata = FileMetadata()
      val update = FileService.updateMetadata(_ => Future.successful(true)) _

      val result = update(metadata).futureValue

      result shouldBe Xor.right(metadata)
    }

    "be not found" in {
      val metadata = FileMetadata()
      val update = FileService.updateMetadata(_ => Future.successful(false)) _

      val result = update(metadata).futureValue

      result shouldBe Xor.left(UpdateMetadataNotFoundError(metadata._id))
    }

    "be a get service error" in {
      val metadata = FileMetadata()
      val update = FileService.updateMetadata(_ => Future.failed(new Exception("not good"))) _

      val result = update(metadata).futureValue

      result shouldBe Xor.left(UpdateMetadataServiceError(metadata._id, "not good"))
    }
  }
}
