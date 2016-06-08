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

import org.joda.time.DateTime
import org.junit
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.models.{Constraints, Envelope}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class EnvelopeRepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication  {

  import Support._


  "repository" should {
    "persist an envelope" in {
      val repository = new EnvelopeRepository(DefaultMongoConnection.db)
      val envelope = Support.envelope

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
  }


}
