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

package uk.gov.hmrc.fileupload.read.envelope

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.FileId
import uk.gov.hmrc.fileupload.Support.fileRefId
import uk.gov.hmrc.fileupload.read.envelope.Service.{FindEnvelopeNotFoundError, FindServiceError}

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends AnyWordSpecLike with Matchers with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "find" should {
    "be successful" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.successful(Some(envelope))) _

      val result = find(envelope._id).futureValue

      result shouldBe Right(envelope)
    }

    "be not found" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.successful(None)) _

      val result = find(envelope._id).futureValue

      result shouldBe Left(FindEnvelopeNotFoundError)
    }

    "be a find service error" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.failed(new Exception("not good"))) _

      val result = find(envelope._id).futureValue

      result shouldBe Left(FindServiceError("not good"))
    }
  }

  "findMetadata" should {
    "be successful" in {
      val file = File(FileId(), fileRefId(), FileStatusQuarantined)
      val envelope = Envelope(files = Some(List(file)))
      val findMetadata = Service.findMetadata(_ => Future.successful(Right(envelope))) _

      val result = findMetadata(envelope._id, file.fileId).futureValue

      result shouldBe Right(file)
    }
  }
}
