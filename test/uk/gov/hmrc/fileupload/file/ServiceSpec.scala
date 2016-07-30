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
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.{FileId, Support}
import uk.gov.hmrc.fileupload.envelope.File
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "getting file metadata" should {
    "be successful if metadata exists" in {
      val fileId = FileId()
      val file = File(fileId = fileId)
      val envelope = Support.envelope.copy(files = Some(List(file)))
      val get = Service.getMetadata(_ => Future.successful(Some(envelope))) _

      val result = get(envelope._id, fileId).futureValue

      result shouldBe Xor.right(file)
    }

    "fail if metadata does not exist" in {
      val envelope = Support.envelope
      val fileId = FileId("NOTEXISTINGFILE")
      val get = Service.getMetadata(_ => Future.successful(None)) _

      val result = get(envelope._id, fileId).futureValue

      result shouldBe Xor.left(GetMetadataNotFoundError)
    }

    "fail if there was an exception" in {
      val envelope = Support.envelope
      val fileId = FileId()
      val get = Service.getMetadata(_ => Future.failed(new Exception("not good"))) _

      val result = get(envelope._id, fileId).futureValue

      result shouldBe Xor.left(GetMetadataServiceError("not good"))
    }
  }

  "downloading a file" should {
    "succeed if file was available" in {
      val fileId = FileId()
      val envelope = Support.envelopeWithAFile(fileId)
      val filename = Some("filename")
      val length = 10
      val data = Enumerator("sth".getBytes())

      val result = Service.retrieveFile(
        getEnvelope = _ => Future.successful(Some(envelope)),
        getFileFromRepo = _ => Future.successful(Some(FileFoundResult(filename, length, data)))
      )(envelope._id, fileId).futureValue

      result shouldBe Xor.Right(FileFoundResult(filename, length, data))
    }
    "fail if file was not available" in {
      val fileId = FileId()
      val envelope = Support.envelopeWithAFile(fileId)

      val result = Service.retrieveFile(
        getEnvelope = _ => Future.successful(Some(envelope)),
        getFileFromRepo = _ => Future.successful(None)
      )(envelope._id, fileId).futureValue

      result shouldBe Xor.Left(GetFileNotFoundError)
    }
    "fail if envelope was not available" in {
      val fileId = FileId()
      val envelope = Support.envelopeWithAFile(fileId)
      val result = Service.retrieveFile(
        getEnvelope = _ => Future.successful(None),
        getFileFromRepo = _ => Future.successful(None)
      )(envelope._id, fileId).futureValue

      result shouldBe Xor.Left(GetFileEnvelopeNotFound)
    }
    "fail if file system reference (fsReference) was not found" in {
      val fileId = FileId()
      val envelope = Support.envelope.copy(files = Some(List(File(fileId, fsReference = None))))

      val result = Service.retrieveFile(
        getEnvelope = _ => Future.successful(Some(envelope)),
        getFileFromRepo = _ => ???
      )(envelope._id, fileId).futureValue

      result shouldBe Xor.Left(GetFileNotFoundError)
    }
  }

}
