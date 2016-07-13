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

package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import play.api.http.Status
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.ByteStream
import uk.gov.hmrc.play.test.UnitSpec

/**
	* Created by jay on 23/06/2016.
	*/
class FileMetadataParserSpec extends UnitSpec {

	val envelopeId = UUID.randomUUID().toString
	val fileId = UUID.randomUUID().toString

	val json =
		s"""
			 |{
       |   "id": "$fileId",
			 |   "filename":"test.pdf",
			 |   "contentType":"application/pdf",
			 |   "revision":1,
			 |   "metadata":{
			 |      "id":"1234567890",
			 |      "origin":{
			 |         "nino":"AB123456Z",
			 |         "token":"48729348729348732894",
			 |         "session":"cd30f8ec-d866-4ae0-82a0-1bc720f1cb09",
			 |         "agent":"292929292",
			 |         "trustedHelper":"8984293480239480",
			 |         "ipAddress":"1.2.3.4"
			 |      },
			 |      "sender":{
			 |         "service":"some-service-identifier/v1.2.33"
			 |      }
			 |   }
			 |}
		 """.stripMargin

	"A FileMetadata body parser" should {
		"return a File 	Metadata when given the appropiate json data" in {
			val fileMetadata = Json.fromJson[UpdateFileMetadataReport](Json.parse(json)).get

			val consumer = Enumerator[ByteStream](json.getBytes)
			val request = FakeRequest[String]("POST", "/envelope", FakeHeaders(), body = "")
			val either = await(consumer(FileMetadataParser(request)).run)
			val parsedFileMatadata = either.right.get

			parsedFileMatadata shouldBe fileMetadata
		}

		"return a result with status 500 when give bad json data" in {
			val consumer = Enumerator[ByteStream]("abc".getBytes)
			val request = FakeRequest[String]("POST", "/envelope", FakeHeaders(), body = "")
			val either = await(consumer(FileMetadataParser(request)).run)
			val result = either.left.get

			result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
		}
	}
}
