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

package uk.gov.hmrc.fileupload.envelope

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "create" should {
    "be successful" in {
      val envelope = Envelope.emptyEnvelope()
      val create = Service.create(_ => Future.successful(true)) _

      val result = create(envelope).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not successful" in {
      val envelope = Envelope.emptyEnvelope()
      val create = Service.create(_ => Future.successful(false)) _

      val result = create(envelope).futureValue

      result shouldBe Xor.left(CreateNotSuccessfulError(envelope))
    }

    "be a create service error" in {
      val envelope = Envelope.emptyEnvelope()
      val create = Service.create(_ => Future.failed(new Exception("not good"))) _

      val result = create(envelope).futureValue

      result shouldBe Xor.left(CreateServiceError(envelope, "not good"))
    }
  }

  "find" should {
    "be successful" in {
      val envelope = Envelope.emptyEnvelope()
      val find = Service.find(_ => Future.successful(Some(envelope))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not found" in {
      val envelope = Envelope.emptyEnvelope()
      val find = Service.find(_ => Future.successful(None)) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindEnvelopeNotFoundError(envelope._id))
    }

    "be a find service error" in {
      val envelope = Envelope.emptyEnvelope()
      val find = Service.find(_ => Future.failed(new Exception("not good"))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindServiceError(envelope._id, "not good"))
    }
  }

  "delete" should {
    "be successful" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be a sealed error" in {
      val envelope = Envelope.emptyEnvelope().copy(status = Sealed)
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeSealedError(envelope))
    }

    "be not successful" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.successful(false), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeNotSuccessfulError(envelope))
    }

    "be delete service error on delete" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good"))
    }

    "be not found" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindEnvelopeNotFoundError(envelope._id)))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeNotFoundError(envelope._id))
    }

    "be delete service error on find service error" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindServiceError(envelope._id, "not good")))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good"))
    }

    "be delete service error on find" in {
      val envelope = Envelope.emptyEnvelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.failed(new Exception("not good on find"))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good on find"))
    }
  }

  "seal" should {
    "be successful" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be a sealed error" in {
      val envelope = Envelope.emptyEnvelope().copy(status = Sealed)
      val seal = Service.seal(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealEnvelopeAlreadySealedError(envelope))
    }

    "be not successful" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.successful(false), _ => Future.successful(Xor.right(envelope))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealEnvelopNotSuccessfulError(envelope))
    }

    "be seal service error on delete" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.failed(new Exception("not good")), _ => Future.successful(Xor.right(envelope))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealServiceError(envelope._id, "not good"))
    }

    "be not found" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.successful(true), _ => Future.successful(Xor.left(FindEnvelopeNotFoundError(envelope._id)))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealEnvelopeNotFoundError(envelope._id))
    }

    "be seal service error on find service error" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.successful(true), _ => Future.successful(Xor.left(FindServiceError(envelope._id, "not good")))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealServiceError(envelope._id, "not good"))
    }

    "be seal service error on find" in {
      val envelope = Envelope.emptyEnvelope()
      val seal = Service.seal(_ => Future.failed(new Exception("not good")), _ => Future.failed(new Exception("not good on find"))) _

      val result = seal(envelope._id).futureValue

      result shouldBe Xor.left(SealServiceError(envelope._id, "not good on find"))
    }
  }

  "addFile" should {
    "be successful" in {
      val envelope = Envelope.emptyEnvelope()
      val fileId = "123"
      val expectedEnvelope = envelope.copy(files = Some(Seq(File(href = s"url/$fileId", id = fileId))))
      val addFile = Service.addFile((_, _) => s"url/$fileId", _ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = addFile(envelope._id, fileId).futureValue

      result shouldBe Xor.right(expectedEnvelope)
    }

    //TODO: add tests for error cases
  }
}
