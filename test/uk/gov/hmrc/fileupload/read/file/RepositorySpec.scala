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

package uk.gov.hmrc.fileupload.read.file

import org.joda.time.{DateTime, Duration}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsString
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepositorySpec extends UnitSpec with MongoSpecSupport with ScalaFutures with BeforeAndAfterEach {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val repository = new Repository(mongo)

	"repository" should {
		"retrieve a file in a envelope" in {
			val text = "I only exists to be stored in mongo :<"
			val contents = Enumerator[ByteStream](text.getBytes)

			val envelopeId = Support.envelope._id
			val fileId = FileId()
			val fileRefId = FileRefId()

			val sink = repository.iterateeForUpload(envelopeId, fileId, fileRefId)
			val fsId = contents.run[Future[JSONReadFile]](sink).futureValue.id match {
				case JsString(v) => FileId(v)
				case _ => fail("expected JsString here")
			}

			val fileResult = repository.retrieveFile(fileRefId).futureValue.get

      fileResult.length shouldBe text.getBytes.length
			val resultAsString = {
				val consume = Iteratee.consume[String]()
				fileResult.data.map(new String(_)).run(consume).futureValue
			}
			resultAsString shouldBe text
		}

		"returns a fileNotFound error" in {
			val nonexistentId = FileRefId()

			val fileResult = repository.retrieveFile(nonexistentId).futureValue

      fileResult shouldBe None
		}

	}

	def insertAnyFile(): String = {
		val text = "I only exists to be stored in mongo :<"
		val contents = Enumerator[ByteStream](text.getBytes)

		val envelopeId = Support.envelope._id
		val fileId = FileId()
		val fileRefId = FileRefId()

		val sink = repository.iterateeForUpload(envelopeId, fileId, fileRefId)
		contents.run[Future[JSONReadFile]](sink).futureValue.id match {
			case JsString(v) => v
			case _ => fail("expected JsString here")
		}
	}
}
