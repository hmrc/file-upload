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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.DefaultMongoConnection
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures {

	def createMetadata(id: String) = FileMetadata(
		_id = id,
		contentType = Some("application/pdf"),
		revision = Some(1),
		filename = Some("test.pdf"),
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
		  val metadata = createMetadata(UUID.randomUUID().toString)

		  val repository = new Repository(mongo)
		  val result = await(repository addFileMetadata metadata)
		  result shouldBe true
	  }

	  "be able to update matadata of an existing file" in {
		  val repository = new Repository(mongo)
		  val contents = Enumerator[ByteStream]("I only exists to be stored in mongo :<".getBytes)
		  val id = UUID.randomUUID().toString


		  val sink = repository.iterateeForUpload(id)

		  await(await(contents.run[Future[JSONReadFile]](sink)))

		  var metadata = await(repository.getFileMetadata(id)).getOrElse(throw new Exception("should have metadata"))
		  val emtpyMetadata = FileMetadata(_id = id)
		  metadata shouldBe emtpyMetadata

		  val updatedMetadata = createMetadata(id)
		  await(repository addFileMetadata updatedMetadata)
		  metadata = await(repository.getFileMetadata(id)).getOrElse(throw new Exception("should have metadata"))

		  println(Json.stringify(Json.toJson[FileMetadata](metadata)))
		  metadata shouldBe updatedMetadata
	  }
  }
}
