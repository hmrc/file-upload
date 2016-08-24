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

package uk.gov.hmrc.fileupload.envelope

import java.lang.Math._
import java.util.UUID

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.junit.Assert.assertTrue
import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Support}
import uk.gov.hmrc.fileupload.controllers.{ConstraintsReport, EnvelopeReport}
import uk.gov.hmrc.fileupload.envelope.Service.UploadedFileInfo
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.{Random, Try}

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
          |  "status": "OPEN"
          |
          |}
        """.stripMargin)

	    val id = EnvelopeId()
	    val result: Envelope = Envelope.fromJson(json, id, maxTTL = 2)

      val contraints = Constraints(contentTypes = Some(Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet")),
        maxItems = Some(100),
        maxSize = Some("12GB"),
        maxSizePerItem = Some("10MB"))

      val expectedResult = Envelope(id, EnvelopeStatusOpen, Some(contraints),
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

			val maxTTL: Int = 2
      val maxExpiryDate: DateTime = DateTime.now().plusDays(maxTTL)

			val envelope = Envelope.fromJson(Json.toJson(Support.farInTheFutureEnvelope), EnvelopeId(), maxTTL)

			assertTrue( isWithinAMinute(maxExpiryDate, envelope.expiryDate) )
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


  "can add a file to a new Envelope" in {
    val envelopeId = EnvelopeId("envelopeId")
    val fileId = FileId("newFile")
    val fsReference = FileId("12334")
    val uploadedFileInfo = UploadedFileInfo(envelopeId = envelopeId, fileId, fsReference, 1L, None)

    val envelope = Envelope().addFile(uploadedFileInfo)

    val expectedFiles = Some(Seq(
      File(fileId = fileId,
        fsReference = Some(fsReference),
        length = Some(uploadedFileInfo.length),
        uploadDate = uploadedFileInfo.uploadDate.map(new DateTime(_))
      )
    ))
    envelope.files shouldBe expectedFiles
  }

  "can add a file to an Envelope with other files" in {
    val envelopeId = EnvelopeId()
    val fileId = FileId("newFile")
    val fsReference = FileId("12334")
    val uploadedFileInfo = UploadedFileInfo(envelopeId = envelopeId, fileId, fsReference, 1L, None)
    val oldFile= File(fileId = FileId("oldFile"))
    val expectedFiles = Some(Seq(
      oldFile,
      File(fileId = fileId,
        fsReference = Some(fsReference),
        length = Some(uploadedFileInfo.length),
        uploadDate = uploadedFileInfo.uploadDate.map(new DateTime(_))
      )
    ))

    val envelope = Envelope(files = Some(Seq(oldFile))).addFile(uploadedFileInfo)

    envelope.files shouldBe expectedFiles
  }

  "can update a file in an Envelope" in {
    val envelopeId = EnvelopeId()
    val fileId = FileId("newfile")
    val fsReference = FileId("12334")
    val file = File(fileId = fileId, fsReference = Some(fsReference))
    val newRef = FileId("newRef-1234")
    val otherFile = File(fileId = FileId("otherFile"))
    val uploadedFileInfo = UploadedFileInfo(envelopeId = envelopeId, fileId, newRef, 1L, None)
    val expectedFiles = Some(Seq(
      otherFile,
      File(fileId = fileId,
        fsReference = Some(newRef),
        length = Some(uploadedFileInfo.length),
        uploadDate = uploadedFileInfo.uploadDate.map(new DateTime(_))
      )
    ))

    val envelope = Envelope(files = Some(Seq(otherFile, file))).addFile(uploadedFileInfo)

    envelope.files shouldBe expectedFiles
  }

  "can add a file metadata to a new Envelope" in {
    val fileId = FileId("newfile")
    val name = "test"
    val metadata: JsObject = Json.obj("a" -> "v")
    val envelope = Envelope().addMetadataToAFile(fileId = fileId, name = Some(name), metadata = Some(metadata))

    envelope.files shouldBe Some(Seq(File(fileId = fileId, name = Some(name), metadata = Some(metadata))))
  }

  "can add a file metadata to an Envelope with other files" in {
    val fileId = FileId("newfile")
    val oldFile = File(fileId = FileId("oldFile"))
    val name = "test"
    val metadata: JsObject = Json.obj("a" -> "v")

    val envelope = Envelope(files = Some(Seq(oldFile))).addMetadataToAFile(fileId = fileId, name = Some(name), metadata = Some(metadata))

    envelope.files shouldBe Some(Seq(oldFile, File(fileId = fileId, name = Some(name), metadata = Some(metadata))))
  }

  "can update a file metadata in an Envelope" in {
    val fileId = FileId("myfile")
    val name = "test"
    val metadata = Json.obj("a" -> "v")
    val file = File(fileId = fileId, name = Some(name), metadata = Some(metadata))

    val newName = "newtest"
    val newMetadata = Json.obj("a" -> "newV")
    val otherFile = File(fileId = FileId("otherFile"))
    val envelope = Envelope(files = Some(Seq(otherFile, file))).addMetadataToAFile(fileId = fileId, name = Some(newName), metadata = Some(newMetadata))

    envelope.files shouldBe Some(Seq(otherFile, File(fileId = fileId, name = Some(newName), metadata = Some(newMetadata))))
  }

  "can add a file status to a new Envelope" in {
    val fileId = FileId("newfile")
    val status = FileStatusQuarantined
    val envelope = Envelope().addStatusToAFile(fileId = fileId, status = status)

    envelope.files shouldBe Some(Seq(File(fileId = fileId, status = Some(status))))
  }

  "can add a file status to an Envelope with other files" in {
    val fileId = FileId("newfile")
    val oldFile = File(fileId = FileId("oldFile"))
    val status = FileStatusQuarantined

    val envelope = Envelope(files = Some(Seq(oldFile))).addStatusToAFile(fileId = fileId, status = status)

    envelope.files shouldBe Some(Seq(oldFile, File(fileId = fileId, status = Some(status))))
  }

  "can update a file status in an Envelope" in {
    val fileId = FileId("myfile")
    val status = FileStatusQuarantined
    val file = File(fileId = fileId, status = Some(status))

    val newStatus = FileStatusAvailable
    val otherFile = File(fileId = FileId("otherFile"))
    val envelope = Envelope(files = Some(Seq(otherFile, file))).addStatusToAFile(fileId = fileId, status = newStatus)

    envelope.files shouldBe Some(Seq(otherFile, File(fileId = fileId, status = Some(newStatus))))
  }

  "can get a file by id" in {
    val fileId = FileId("newfile")
    val file = File(fileId = fileId)
    val files = Random.shuffle(Seq(File(fileId = FileId("foo")), file, File(fileId = FileId("bar"))))
    val envelope = Envelope(files = Some(files))

    envelope.getFileById(fileId) shouldBe Some(file)
  }

  "should respond None when getting a file not existing in the envelope" in {
    val files = Random.shuffle(Seq(File(fileId = FileId("foo")), File(fileId = FileId("bar"))))
    val envelope = Envelope(files = Some(files))

    envelope.getFileById(FileId("wrongid")) shouldBe None
  }



  def isWithinAMinute(maxExpiryDate: DateTime, expiryDate: Option[DateTime]): Boolean = {
    expiryDate.exists(d => abs(d.getMillis - maxExpiryDate.getMillis) < 60 * 1000)
	}
}
