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
import reactivemongo.api.gridfs.DefaultReadFile
import reactivemongo.bson.{BSONDocument, BSONString}
import uk.gov.hmrc.fileupload.{JSONReadFile, Support}
import uk.gov.hmrc.fileupload.envelope.Service._
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

      result shouldBe Xor.left(FindEnvelopeNotFoundError(envelope._id))
    }

    "be a find service error" in {
      val envelope = Envelope()
      val find = Service.find(_ => Future.failed(new Exception("not good"))) _

      val result = find(envelope._id).futureValue

      result shouldBe Xor.left(FindServiceError(envelope._id, "not good"))
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

      result shouldBe Xor.left(DeleteEnvelopeNotSuccessfulError(envelope))
    }

    "be delete service error on delete" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.successful(Xor.right(envelope))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good"))
    }

    "be not found" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindEnvelopeNotFoundError(envelope._id)))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteEnvelopeNotFoundError(envelope._id))
    }

    "be delete service error on find service error" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.successful(true), _ => Future.successful(Xor.left(FindServiceError(envelope._id, "not good")))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good"))
    }

    "be delete service error on find" in {
      val envelope = Envelope()
      val delete = Service.delete(_ => Future.failed(new Exception("not good")), _ => Future.failed(new Exception("not good on find"))) _

      val result = delete(envelope._id).futureValue

      result shouldBe Xor.left(DeleteServiceError(envelope._id, "not good on find"))
    }
  }

  "upsert a file" should {
    "be successful" in {
      val envelope = Envelope()
      val fileId = "123"
      val expectedEnvelope = envelope.copy(files = Some(Seq(File(href = Some(s"url/$fileId"), fileId = fileId))))
      val upsertFile = Service.uploadFile(
        getEnvelope = _ => Future.successful(Some(expectedEnvelope)),
        updateEnvelope = _ => Future.successful(true)
      ) _
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, "fsReference", 1L, None)

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.right(UpsertFileSuccess)
    }

    "fail if envelope with a given id didn't exist" in {
      val envelope = Envelope()
      val fileId = "123"
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, "fsReference", 1L, None)
      val upsertFile = Service.uploadFile(
        getEnvelope = _ => Future.successful(None),
        updateEnvelope = _ => ???
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileEnvelopeNotFoundError(envelope._id))
    }

    "fail if getting an envelope failed due to unknown exception (e.g. network error)" in {
      val envelope = Envelope()
      val fileId = "123"
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, "fsReference", 1L, None)
      val upsertFile = Service.uploadFile(
        getEnvelope = _ => Future.failed(new Exception("network error")),
        updateEnvelope = _ => ???
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileServiceError("network error"))
    }

    "fail if updating an envelope failed" in {
      val envelope = Envelope()
      val fileId = "123"
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, "fsReference", 1L, None)
      val upsertFile = Service.uploadFile(
        getEnvelope = _ => Future.successful(Some(envelope)),
        updateEnvelope = _ => Future.successful(false)
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileUpdatingEnvelopeFailed)
    }

    "fail if updating an envelope failed due to unknown exception (e.g. network error)" in {
      val envelope = Envelope()
      val fileId = "123"
      val uploadedFileInfo = UploadedFileInfo(envelope._id, fileId, "fsReference", 1L, None)
      val upsertFile = Service.uploadFile(
        getEnvelope = _ => Future.successful(Some(envelope)),
        updateEnvelope = _ => Future.failed(new Exception("network error"))
      ) _

      val result = upsertFile(uploadedFileInfo).futureValue

      result shouldBe Xor.left(UpsertFileServiceError("network error"))
    }
  }

  "update metadata" should {
    "be successful (happy path)" in {
      val envelope = Support.envelope
      val update = Service.updateMetadata(_ => Future.successful(Some(envelope)), _ => Future.successful(true)) _

      val result = update("envelopeId", "fileid", Some("file.txt"), Some("appliation/xml"), Some(Json.obj("a" -> "test"))).futureValue

      result shouldBe Xor.right(UpdateMetadataSuccess)
    }

    "fail when envelope was not found" in {
      val envelope = Support.envelope
      val update = Service.updateMetadata(_ => Future.successful(None), _ => Future.successful(true)) _

      val newEnvelopeId = "newEnvelopeId"
      val result = update(newEnvelopeId, "fileid", Some("file.txt"), Some("appliation/xml"), Some(Json.obj("a" -> "test"))).futureValue

      result shouldBe Xor.left(UpdateMetadataEnvelopeNotFoundError)
    }

    "fail when updating an envelope failed" in {
      val envelope = Support.envelope
      val update = Service.updateMetadata(_ => Future.successful(Some(envelope)), _ => Future.successful(false)) _

      val result = update("envelopeId", "fileid", Some("file.txt"), Some("appliation/xml"), Some(Json.obj("a" -> "test"))).futureValue

      result shouldBe Xor.left(UpdateMetadataNotSuccessfulError)
    }

    "fail if there was another exception" in {
      val envelope = Support.envelope
      val update = Service.updateMetadata(_ => Future.successful(Some(envelope)), _ => Future.failed(new Exception("not good"))) _

      val newEnvelopeId = "newEnvelopeId"
      val result = update(newEnvelopeId, "fileid", Some("file.txt"), Some("application/xml"), Some(Json.obj("a" -> "test"))).futureValue

      result shouldBe Xor.left(UpdateMetadataServiceError("not good"))
    }
  }

}
