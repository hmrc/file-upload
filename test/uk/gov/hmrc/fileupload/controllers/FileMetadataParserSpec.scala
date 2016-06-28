package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import play.api.http.Status
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.ByteStream
import uk.gov.hmrc.fileupload.models.FileMetadata
import uk.gov.hmrc.play.test.UnitSpec

/**
	* Created by jay on 23/06/2016.
	*/
class FileMetadataParserSpec extends UnitSpec {

	val id = UUID.randomUUID().toString

	val json =
		s"""
			 |{
			 |   "_id":"$id",
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
		"return a FileMetadata when given the appropiate json data" in {
			import FileMetadata._
			val fileMetadata = Json.fromJson[FileMetadata](Json.parse(json)).get

			val consumer = Enumerator[ByteStream](json.getBytes)
			val request = FakeRequest[String]("POST", "/envelope",  FakeHeaders(), body =  "")
			val either = await(consumer(FileMetadataParser(request)).run)
			val parsedFileMatadata = either.right.get

			parsedFileMatadata shouldBe fileMetadata
		}
		"return a result with status 400 when give bad json data" in {
			val consumer = Enumerator[ByteStream]("{}".getBytes)
			val request = FakeRequest[String]("POST", "/envelope",  FakeHeaders(), body =  "")
			val either = await(consumer(FileMetadataParser(request)).run)
			val result = either.left.get

			result.header.status shouldBe Status.BAD_REQUEST

		}
	}

}
