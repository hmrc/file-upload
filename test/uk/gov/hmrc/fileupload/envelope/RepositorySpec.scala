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

import java.util.UUID

import org.junit
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file._
import uk.gov.hmrc.fileupload.infrastructure.DefaultMongoConnection
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures  {

	"repository" should {
    "persist an envelope" in {
      val repository = new Repository(mongo)
			val envelope = Support.envelope.copy(files = Some(Seq(File(href = "ads", id = "myfile", rel = "file"))))

      val result = await(repository add envelope)
      result shouldBe true
    }

    "retrieve a persisted envelope" in {
      val repository = new Repository(mongo)
      val envelope = Support.envelope
      val id = envelope._id

      await(repository add envelope)
      val result: Option[Envelope] = await(repository get id)

      result shouldBe Some(envelope)
    }

	  "remove a persisted envelope" in {
		  val repository = new Repository(mongo)
		  val envelope = Support.envelope
		  val id = envelope._id

		  await(repository add envelope)
		  val result: Boolean = await(repository delete id)

		  result shouldBe true
		  junit.Assert.assertTrue( await(repository get id).isEmpty )
	  }

	  "return nothing for a none existent envelope" in {
		  val repository = new Repository(mongo)
			val id = UUID.randomUUID().toString

		  val result = await(repository get id)
		  result shouldBe None
	  }
  }
}
