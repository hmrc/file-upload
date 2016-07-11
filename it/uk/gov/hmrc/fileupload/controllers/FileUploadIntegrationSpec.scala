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

import org.scalatest.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class FileUploadIntegrationSpec extends UnitSpec with OneServerPerSuite with ScalaFutures with IntegrationPatience with Matchers with Status{

	override lazy val port: Int = 9000

	val nextId = () => UUID.randomUUID().toString

	val support = new FileUploadSupport

	val poem =     """
		               |Do strangers make you human
		               |Science fiction visiting bodies as cold fact
		               |What unknown numbers govern our genes or phones
		               |A constant thrum from outer space
		               |Snow makes a sound in sand
		               |You are seen from far far above
		               |Unheard and vanished
		               |bodies dismember to dirt
		               |Hardly alive, hardly a person anymore
		               |Who will I be next and in that life will you know me
	               """.stripMargin

	val data = poem.getBytes

	"Application" should{
//		"be able to process an upload request" in  {
//			val id = nextId()
//			val response = support.withEnvelope.doUpload(data, fileId = id)
//			val Some(Seq(file, _*)) = support.refresh.envelope.files
//
//			response.status shouldBe OK
//			file.id shouldBe id
//			// storedPoem shouldBe poem
//		}
		"be able to create file metadata" in {
			val id = nextId()

			val json = FileMetadataSupport.requestBody()

			val response = support.withEnvelope.putFileMetadata(json, id)
			val expectedMetadata =Json.prettyPrint(FileMetadataSupport.responseBodyAsJson(id))
			val actualMetadata = Json.prettyPrint(Json.parse(support.withEnvelope.getFileMetadataFor(id)))

			response.status shouldBe OK
			expectedMetadata shouldBe actualMetadata
		}
	}
}
