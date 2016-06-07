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

package uk.gov.hmrc.fileupload.models

import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Assert
import org.junit.Assert.assertTrue
import play.api.libs.json.{JsString, Json}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Try


class EnvelopeSpec extends UnitSpec {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)


  "a json value" should {
    "be parsed to an envelope object" in {
	    val formattedExpiryDate: String = formatter.print(today)
	    val json = Json.parse(
        s"""
          |{"constraints": {
          |    "contentTypes": [
          |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          |      "application/vnd.oasis.opendocument.spreadsheet"
          |    ],
          |    "maxItems": 100,
          |    "maxSize": "12GB",
          |    "maxSizePerItem": "10MB"
          |  },
          |  "callbackUrl": "http://absolute.callback.url",
          |  "expiryDate": "$formattedExpiryDate",
          |  "metadata": {
          |    "anything": "the caller wants to add to the envelope"
          |  }
          |}
        """.stripMargin)

	    val objectID: BSONObjectID = BSONObjectID.generate
	    val result: Envelope = Envelope.fromJson(json, objectID, maxTTL = 2)

      val contraints = Constraints(contentTypes = Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet"),
        maxItems = 100,
        maxSize = "12GB",
        maxSizePerItem = "10MB")

      val expectedResult = Envelope(objectID, contraints,
                                    callbackUrl = "http://absolute.callback.url",
                                    expiryDate = formatter.parseDateTime(formattedExpiryDate),
                                    metadata = Map("anything" -> JsString("the caller wants to add to the envelope")))

      result shouldEqual expectedResult
    }
  }

	"an envelope" should {
		"not be created when has an expiry date in the past" in {

			assertTrue(Try(Support.envelope.copy(expiryDate = DateTime.now().minusMinutes(3))).isFailure )
		}
	}
}