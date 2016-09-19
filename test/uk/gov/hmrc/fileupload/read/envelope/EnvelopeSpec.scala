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

package uk.gov.hmrc.fileupload.read.envelope

import java.lang.Math._

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.controllers.{ConstraintsReport, EnvelopeReport}
import uk.gov.hmrc.fileupload.write.infrastructure.Version
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Random}

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
          |  },
          |  "status": "OPEN",
          |  "version": 1
          |}
        """.stripMargin)

	    val id = EnvelopeId()

	    val result: Envelope = Envelope.fromJson(json, id)

      val contraints = Constraints(contentTypes = Some(Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet")),
        maxItems = Some(100),
        maxSize = Some("12GB"),
        maxSizePerItem = Some("10MB"))

      val expectedResult = Envelope(id, Version(1), EnvelopeStatusOpen, Some(contraints),
                                    callbackUrl = Some("http://absolute.callback.url"),
                                    expiryDate = Some(formatter.parseDateTime(formattedExpiryDate)),
                                    metadata = Some(Map("anything" -> JsString("the caller wants to add to the envelope"))), None)

      result shouldEqual expectedResult
    }
  }

  "an envelope" should {
    "have maxItems constrain defaulted to 1 when not specified" in {
      val dto: EnvelopeReport = EnvelopeReport(constraints = Some(ConstraintsReport(maxItems = None)))

      val envelope: Envelope = EnvelopeReport.toEnvelope(EnvelopeId("abc"), dto)

      envelope.constraints.get.maxItems should equal( Some(1) )
    }
  }

  "an envelope" should {
    "have maxItems constrain NOT defaulted to 1 when specified" in {
      val dto: EnvelopeReport = EnvelopeReport(constraints = Some(ConstraintsReport(maxItems = Some(2))))

      val envelope: Envelope = EnvelopeReport.toEnvelope(EnvelopeId("abc"), dto)

      envelope.constraints.get.maxItems should equal( Some(2) )
    }
  }

  "can get a file by id" in {
    val fileId = FileId("newfile")
    val fileRefId = FileRefId("newfileref")
    val file = File(fileId = fileId, fileRefId = fileRefId, FileStatusQuarantined)
    val files = Random.shuffle(Seq(
      File(fileId = FileId("foo"), fileRefId = FileRefId("foo-rf"), FileStatusQuarantined),
      file,
      File(fileId = FileId("bar"), fileRefId = FileRefId("bar-rf"), FileStatusQuarantined)))
    val envelope = Envelope(files = Some(files))

    envelope.getFileById(fileId) shouldBe Some(file)
  }

  "should respond None when getting a file not existing in the envelope" in {
    val files = Random.shuffle(Seq(
      File(fileId = FileId("foo"), fileRefId = FileRefId("foo-rf"), FileStatusQuarantined),
      File(fileId = FileId("bar"), fileRefId = FileRefId("bar-rf"), FileStatusQuarantined)))
    val envelope = Envelope(files = Some(files))

    envelope.getFileById(FileId("wrongid")) shouldBe None
  }

  def isWithinAMinute(maxExpiryDate: DateTime, expiryDate: Option[DateTime]): Boolean = {
    expiryDate.exists(d => abs(d.getMillis - maxExpiryDate.getMillis) < 60 * 1000)
	}
}
