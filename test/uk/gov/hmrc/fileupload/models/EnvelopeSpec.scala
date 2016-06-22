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

import java.lang.Math._
import java.util.UUID

import akka.actor.{ActorRef, Inbox}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Assert
import org.junit.Assert.assertTrue
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.EnvelopeService
import uk.gov.hmrc.fileupload.actors.EnvelopeService.CreateEnvelope
import uk.gov.hmrc.fileupload.actors.Storage.Save
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

	    val id: String = UUID.randomUUID().toString
	    val result: Envelope = Envelope.fromJson(json, id, maxTTL = 2)

      val contraints = Constraints(contentTypes = Some(Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet")),
        maxItems = Some(100),
        maxSize = Some("12GB"),
        maxSizePerItem = Some("10MB"))

      val expectedResult = Envelope(id, Some(contraints),
                                    callbackUrl = Some("http://absolute.callback.url"),
                                    expiryDate = Some(formatter.parseDateTime(formattedExpiryDate)),
                                    metadata = Some(Map("anything" -> JsString("the caller wants to add to the envelope"))), None)

      result shouldEqual expectedResult
    }
  }

	"an envelope" should {
		"not be created when it has an expiry date in the past" in {

			assertTrue(Try(Support.envelope.copy(expiryDate = Some(DateTime.now().minusMinutes(3)))).isFailure )
		}
	}

	"an envelope's" should {
		"expiryDate should be overridden when it is greater than the max expiry days configured" in {

			import Envelope._

			val maxTTL: Int = 2
      val maxExpiryDate: DateTime = DateTime.now().plusDays(maxTTL)

			val envelope = Envelope.fromJson(Json.toJson(Support.farInTheFutureEnvelope), UUID.randomUUID().toString, maxTTL)

			assertTrue( isWithinAMinute(maxExpiryDate, envelope.expiryDate) )
		}
	}

	def isWithinAMinute(maxExpiryDate: DateTime, expiryDate: Option[DateTime]): Boolean = {
    expiryDate.exists(d => abs(d.getMillis - maxExpiryDate.getMillis) < 60 * 1000)
	}
}