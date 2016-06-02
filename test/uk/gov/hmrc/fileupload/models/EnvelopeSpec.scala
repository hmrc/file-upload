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

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec


class EnvelopeSpec extends UnitSpec {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().withMillis(0L)


  "a json value" should {
    "be parsed to an envelope object" in {
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
          |  "expiryDate": "${formatter.print(today)}",
          |  "metadata": {
          |    "anything": "the caller wants to add to the envelope"
          |  }
          |}
        """.stripMargin)

      val result: Envelope = json.as[Envelope]

      val contraints = Constraints(contentTypes = Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet"),
        maxItems = 100,
        maxSize = "12GB",
        maxSizePerItem = "10MB")

      val expectedResult = Envelope(constraints = contraints,
                                    callbackUrl = "http://absolute.callback.url",
                                    expiryDate = today,
                                    metadata = Map("anything" -> "the caller wants to add to the envelope"))

      result shouldEqual expectedResult
    }
  }
}