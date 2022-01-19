/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.write.infrastructure.Version
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.util.Random

class EnvelopeSpec extends AnyWordSpecLike with Matchers {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  "a json value" should {
    "be parsed to an envelope object" in {
	    val formattedExpiryDate: String = formatter.print(today)
	    val json = Json.parse(
        s"""
          |{
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

      val expectedResult = Envelope(id, Version(1), EnvelopeStatusOpen,
                                    constraints = None,
                                    callbackUrl = Some("http://absolute.callback.url"),
                                    expiryDate = Some(formatter.parseDateTime(formattedExpiryDate)),
                                    metadata = Some(Json.obj("anything" -> "the caller wants to add to the envelope")),
                                    isPushed = None)

      result shouldEqual expectedResult
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
