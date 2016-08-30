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
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Support}
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.events.FileUploadedAndAssigned
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "create" should {
    "be successful" in {
      val envelope = Envelope()
      val create = Service.create(_ => Future.successful(true)) _

      val result = create(envelope).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not successful" in {
      val envelope = Envelope()
      val create = Service.create(_ => Future.successful(false)) _

      val result = create(envelope).futureValue

      result shouldBe Xor.left(CreateNotSuccessfulError(envelope))
    }

    "be a create service error" in {
      val envelope = Envelope()
      val create = Service.create(_ => Future.failed(new Exception("not good"))) _

      val result = create(envelope).futureValue

      result shouldBe Xor.left(CreateServiceError(envelope, "not good"))
    }
  }

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

  "delete" should {
    "be successful" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.right(envelope)
    }

    "be not successful" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(false), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeNotSuccessfulError)
    }

    "be delete service error on delete" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError("not good"))
    }

    "be not found" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindEnvelopeNotFoundError))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeNotFoundError)
    }

    "be delete service error on find service error" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindServiceError("not good")))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError("not good"))
    }

    "be delete service error on find" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.failed(new Exception("not good on find"))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError("not good on find"))
    }
  }

  "upsert a file" should {
    "be successful" in {
      var collector: AnyRef = null
      val publisher = (event:AnyRef) => {
        collector = event
      }

      val envelope = Envelope()
      val fileId = FileId()
      val upsertFile = Service.uploadFile(
        upsertFile = (_,_) => Future.successful(true),
        publish = publisher
      ) _
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, FileId("fsReference"), 1L, None)

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.right(UpsertFileSuccess)
      collector shouldBe FileUploadedAndAssigned(envelope._id, fileId)
    }

    "fail if updating an envelope failed" in {
      val envelope = Envelope()
      val fileId = FileId()
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, FileId("fsReference"), 1L, None)
      val upsertFile = Service.uploadFile(
        upsertFile = (_,_) => Future.successful(false),
        publish = _ => Unit
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileUpdatingEnvelopeFailed)
    }

    "fail if updating an envelope failed due to unknown exception (e.g. network error)" in {
      val envelope = Envelope()
      val fileId = FileId()
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, FileId("fsReference"), 1L, None)
      val upsertFile = Service.uploadFile(
        upsertFile = (_,_) => Future.failed(new Exception("network error")),
        publish = _ => Unit
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileServiceError("network error"))
    }
  }

  "update metadata" should {
    "be successful (happy path)" in {
      val update = Service.updateMetadata(_ => Future.successful(true)) _

      val result = await(update(UploadedFileMetadata(
        EnvelopeId("envelopeId"), FileId(), Some("file.txt"), Some("application/xml"), Some(Json.obj("a" -> "test"))
      )))

      result shouldBe Xor.right(UpdateMetadataSuccess)
    }

    "fail when envelope was not found" in {
      pending
//      val update = Service.updateMetadata(_ => Future.successful(None), _ => Future.successful(true)) _
//
//      val newEnvelopeId = EnvelopeId("newEnvelopeId")
//      val result = await(update(newEnvelopeId, FileId(), Some("file.txt"), Some("application/xml"), Some(Json.obj("a" -> "test"))))
//
//      result shouldBe Xor.left(UpdateMetadataEnvelopeNotFoundError)
    }

    "fail when updating an envelope failed" in {
      val update = Service.updateMetadata(_ => Future.successful(false)) _

      val result = await(update(
        UploadedFileMetadata(EnvelopeId("envelopeId"), FileId(), Some("file.txt"), Some("application/xml"), Some(Json.obj("a" -> "test")))
      ))

      result shouldBe Xor.left(UpdateMetadataNotSuccessfulError)
    }

    "fail if there was another exception" in {
      val update = Service.updateMetadata(_ => Future.failed(new Exception("not good"))) _

      val newEnvelopeId = EnvelopeId("newEnvelopeId")
      val result = await(update(
        UploadedFileMetadata(newEnvelopeId, FileId(), Some("file.txt"), Some("application/xml"), Some(Json.obj("a" -> "test")))
      ))

      result shouldBe Xor.left(UpdateMetadataServiceError("not good"))
    }
  }

  "delete file" should {
    "be successful (happy path)" in {
      val deleteFile = Service.deleteFile((_, _) => Future.successful(true)) _

      val result = deleteFile(EnvelopeId("envelopeId"), FileId("fileId")).futureValue

      result shouldBe Xor.right(FileId("fileId"))
    }

    "fail when envelope was not found" in {
      val deleteFile = Service.deleteFile((_, _) => Future.successful(false)) _

      val result = deleteFile(EnvelopeId("newEnvelopeId"), FileId()).futureValue

      result shouldBe Xor.left(DeleteFileNotFoundError)
    }

    "fail if there was another exception" in {
      val deleteFile = Service.deleteFile((_, _) => Future.failed(new Exception("not good"))) _

      val result = deleteFile(EnvelopeId("newEnvelopeId"), FileId()).futureValue

      result shouldBe Xor.left(DeleteFileServiceError("not good"))
    }
  }
}
