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

package uk.gov.hmrc.fileupload.repositories

import java.util.UUID

import org.joda.time.DateTime
import org.junit
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsString, JsObject, Format, Json}
import play.modules.reactivemongo.JSONFileToSave
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.models._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class EnvelopeRepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures  {

  import Support._

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
    "persist an envelope" in {
      val repository = new EnvelopeRepository(DefaultMongoConnection.db)
			val envelope = Support.envelope.copy(files = Some(Seq(File(href = "ads", id = "myfile", rel = "file"))))

      val result = await(repository add envelope)
      result shouldBe true

    }

    "retrieve a persisted envelope" in {
      val repository = new EnvelopeRepository(DefaultMongoConnection.db)
      val envelope = Support.envelope
      val id = envelope._id

      await(repository add envelope)
      val result: Option[Envelope] = await(repository get id)

      result shouldBe Some(envelope)

    }

	  "remove a persisted envelope" in {
		  val repository = new EnvelopeRepository(DefaultMongoConnection.db)
		  val envelope = Support.envelope
		  val id = envelope._id

		  await(repository add envelope)
		  val result: Boolean = await(repository delete id)

		  result shouldBe true
		  junit.Assert.assertTrue( await(repository get id).isEmpty )
	  }

	  "return nothing for a none existent envelope" in {
		  val repository = new EnvelopeRepository(DefaultMongoConnection.db)
			val id = UUID.randomUUID().toString

		  val result = await(repository get id)
		  result shouldBe None
	  }

	  "adds a file to a envelope" in {
		  val repository = new EnvelopeRepository(DefaultMongoConnection.db)
		  val envelope = Support.envelope
		  val id = envelope._id
		  await(repository add envelope)

		  val result = await( repository.addFile( id, fileId = "456") )
		  result shouldBe true
	  }

	  "add file metadata" in {
		  val meatadata = createMetadata(UUID.randomUUID().toString)

		  val repository = new EnvelopeRepository(DefaultMongoConnection.db)
		  val result = await(repository addFile meatadata)
		  result shouldBe true
	  }

	  "be able to update matadata of an existing file" in {
		  val repository = new EnvelopeRepository(DefaultMongoConnection.db)
		  val contents = Enumerator[ByteStream]("I only exists to be stored in mongo :<".getBytes)
		  val id = UUID.randomUUID().toString


		  val sink = repository.iterateeForUpload(id)

		  await(await(contents.run[Future[JSONReadFile]](sink)))

		  var metadata = await(repository.getFileMetadata(id)).getOrElse(throw new Exception("should have metadata"))
		  val emtpyMetadata = FileMetadata(_id = id)
		  metadata shouldBe emtpyMetadata

		  val updatedMetadata = createMetadata(id)
		  await(repository addFile updatedMetadata)
		  metadata = await(repository.getFileMetadata(id)).getOrElse(throw new Exception("should have metadata"))

		  metadata shouldBe updatedMetadata
	  }

  }


}
