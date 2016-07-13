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

import java.util.UUID

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file.Repository.{FileNotFoundError, RetrieveFileResult}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures with BeforeAndAfterEach {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val repository = new Repository(mongo)

  override def beforeEach {
    repository.removeAll().futureValue
  }

  def createMetadata(compositeFileId: CompositeFileId = CompositeFileId(UUID.randomUUID().toString, UUID.randomUUID().toString)) = FileMetadata(
		_id = compositeFileId,
		contentType = Some("application/pdf"),
		revision = Some(1),
    length = Some(38),
		name = Some("test.pdf"),
		metadata = Some(Json.parse(
			"""
				| {
				|   "id" : "1234567890",
				|   "origin": {
				|     "nino" : "AB123456Z",
				|     "token": "48729348729348732894",
				|     "session": "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09",
				|     "agent" : "292929292",
				|     "trustedHelper" : "8984293480239480",
				|     "ipAddress" : "1.2.3.4"
				|   },
				|   "sender" : {
				|     "service" : "some-service-identifier/v1.2.33"
				|    }
				| }
			""".stripMargin).asInstanceOf[JsObject]
		)
	)

	"repository" should {
	  "add file metadata" in {
		  val metadata = createMetadata()

		  val result = (repository addFileMetadata metadata).futureValue
      result shouldBe true
	  }

	  "be able to update metadata of an existing file" in {
			val bytes = "I only exists to be stored in mongo :<".getBytes
		  val contents = Enumerator[ByteStream](bytes)

			val fileId = UUID.randomUUID().toString
      val envelopeId: String = Support.envelope._id
			val compositeFileId = CompositeFileId(envelopeId, fileId)

      val sink = repository.iterateeForUpload(compositeFileId)

		  contents.run[Future[JSONReadFile]](sink).futureValue.futureValue

		  var metadata = (repository.getFileMetadata(compositeFileId)).futureValue.getOrElse(throw new Exception("should have metadata"))
      val fileMetadata = FileMetadata(_id = compositeFileId, length = Some(bytes.length), uploadDate = metadata.uploadDate)
		  metadata shouldBe fileMetadata

      val updatedMetadata = createMetadata(compositeFileId)
      (repository addFileMetadata updatedMetadata).futureValue
      metadata = repository.getFileMetadata(compositeFileId).futureValue.getOrElse(throw new Exception("should have metadata"))

		  metadata shouldBe updatedMetadata.copy(uploadDate = metadata.uploadDate)
	  }

		"retrieve a file in a envelope" in {
			val contents = Enumerator[ByteStream]("I only exists to be stored in mongo :<".getBytes)

			val envelopeId = Support.envelope._id
			val fileId = UUID.randomUUID().toString
			val compositeFileId = CompositeFileId(envelopeId, fileId)

			val sink = repository.iterateeForUpload(compositeFileId)
			contents.run[Future[JSONReadFile]](sink).futureValue

			val fileResult: RetrieveFileResult = (repository retrieveFile compositeFileId).futureValue

      fileResult.isRight shouldBe true
      fileResult.toEither.right.get.length shouldBe 38
		}

		"returns a fileNotFound error" in {
			val compositeFileId = CompositeFileId(Support.envelope._id, "nofile")

			val fileResult: RetrieveFileResult = (repository retrieveFile compositeFileId).futureValue

      fileResult.isLeft shouldBe true
      fileResult.toEither.left.get shouldBe FileNotFoundError
		}
  }
}
