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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Support}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class RepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures with BeforeAndAfterEach  {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val repository = new Repository(mongo)

  override def beforeEach {
    repository.removeAll().futureValue
  }

	"repository" should {
    "persist an envelope" in {
      val file = File(fileId = FileId())
      val envelope = Support.envelope.copy(files = Some(Seq(file)))

      val result = (repository add envelope).futureValue
      result shouldBe true

      val persistedEnvelope = repository.get(envelope._id).futureValue.get
      persistedEnvelope.callbackUrl shouldBe Some("http://absolute.callback.url")
      persistedEnvelope.getFileById(file.fileId) shouldBe Some(file)

    }

    "retrieve a persisted envelope" in {
      val envelope = Support.envelope
      val id = envelope._id

      (repository add envelope).futureValue
      val result: Option[Envelope] = (repository get id).futureValue

      result shouldBe Some(envelope)
    }

	  "remove a persisted envelope" in {
		  val envelope = Support.envelope
		  val id = envelope._id

      (repository add envelope).futureValue
      val result: Boolean = (repository delete id).futureValue

      result shouldBe true
      junit.Assert.assertTrue((repository get id).futureValue.isEmpty)
	  }

	  "return nothing for a none existent envelope" in {
			val id = EnvelopeId()

		  val result = repository.get(id).futureValue
      result shouldBe None
	  }
  }
}
