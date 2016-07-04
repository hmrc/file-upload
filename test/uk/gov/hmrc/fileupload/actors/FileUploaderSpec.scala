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

package uk.gov.hmrc.fileupload.actors

import play.api.libs.json.JsValue
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.Support.EnvelopRepositoryStub
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.{Await, Future}

import org.scalatest.mock.MockitoSugar
import play.api.libs.iteratee.Iteratee
import akka.pattern._
import scala.concurrent.duration._
import scala.language.postfixOps

class FileUploaderSpec extends ActorSpec with MockitoSugar {

	"File uploader" should {
		"check if the envelope where to put the file exists" in {
		}
	}

	"File uploader" should {
		"store the file in GFS" in {

			implicit  val ec = system.dispatcher
			val source = Seq("this is", " a stream to", " give in input").map(_.getBytes())
			var buffer: String = ""
			val gfsResult = mock[ReadFile[JSONSerializationPack.type, JsValue]]
			val fileId = "testfileid"
			val future: Future[JSONReadFile] = Future.successful(gfsResult)
			val sink : Iteratee[ByteStream, Future[JSONReadFile]] = Iteratee.foreach[ByteStream]{ b => buffer = buffer + new String(b)}.map{ _ => future }

			val repo: EnvelopeRepository = new EnvelopRepositoryStub( iteratee = sink)
			val fileUploader = system.actorOf(FileUploader.props( "testenvelopeid",  fileId, repo))

			type expectedType = Iteratee[ByteStream, Future[JSONReadFile]]

			source.foreach(fileUploader ! _)

			val result = Await.result((fileUploader ? FileUploader.EOF).mapTo[FileUploader.Status], 500 millis)
			result shouldBe FileUploader.Completed
			buffer shouldBe "this is a stream to give in input"
		}
	}
}
