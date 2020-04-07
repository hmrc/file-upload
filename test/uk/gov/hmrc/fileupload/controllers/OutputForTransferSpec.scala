/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.read.envelope._
import uk.gov.hmrc.play.test.UnitSpec

class OutputForTransferSpec extends UnitSpec with ApplicationComponents {

  "Presenting individual files" should {
    "include all fields if values are present" in {

      val dateAsText = "2016-03-31T12:33:45Z"
      val f = File(
        fileId = FileId(),
        fileRefId = FileRefId(),
        status = FileStatusAvailable,
        name = Some("original-file-name-on-disk.docx"),
        contentType = Some("application/vnd.oasis.opendocument.spreadsheet"),
        length = Some(1231222),
        uploadDate = Some(DateTime.parse(dateAsText))
      )
      val envelope = Support.envelope.copy(files = Some(Seq(f)))

      val expectedJson = withRemovedWhitespace {
        s"""
             {
               "href": "/file-upload/envelopes/${envelope._id}/files/${f.fileId}/content",
               "name": "${f.name.get}",
               "contentType": "${f.contentType.get}",
               "length": ${f.length.get},
               "created": "$dateAsText",
               "_links": {
                 "self": {
                   "href": "/file-upload/envelopes/${envelope._id}/files/${f.fileId}"
                 }
               }
             }

        """
      }

      val actualJson = OutputForTransfer.stringifyFile(envelope, f)

      withClue("actualJson = " + Json.prettyPrint(actualJson)) {
        actualJson.toString shouldBe expectedJson
      }
    }

    "keep keys for [name, contentType, length, created] even if values are not available" in {
      val f = File(
        fileId = FileId(),
        fileRefId = FileRefId(),
        status = FileStatusAvailable,
        name = None,
        contentType = None,
        length = None,
        uploadDate = None
      )
      val envelope = Support.envelope.copy(files = Some(Seq(f)))

      val expectedJson = withRemovedWhitespace {
        s"""
             {
               "href": "/file-upload/envelopes/${envelope._id}/files/${f.fileId}/content",
               "name": null,
               "contentType": null,
               "length": null,
               "created": null,
               "_links": {
                 "self": {
                   "href": "/file-upload/envelopes/${envelope._id}/files/${f.fileId}"
                 }
               }
             }

        """
      }

      val actualJson = OutputForTransfer.stringifyFile(envelope, f)

      withClue("actualJson = " + Json.prettyPrint(actualJson)) {
        actualJson.toString shouldBe expectedJson
      }
    }


  }

  "Presenting envelopes as part of file-transfer" should {
    "include all required HATEOAS boilerplate" in {
      val dateAsText = "2016-03-31T12:33:45Z"
      val file = File(
        fileId = FileId(),
        fileRefId = FileRefId(),
        status = FileStatusAvailable,
        name = Some("original-file-name-on-disk.docx"),
        contentType = Some("application/vnd.oasis.opendocument.spreadsheet"),
        length = Some(1231222),
        uploadDate = Some(DateTime.parse(dateAsText))
      )
      val envelope = Envelope(
        destination = Some("DMS"),
        application = Some("app/sth/sthElse"),
        files = Some(List(file))
      )

      val destination = "DMS"
      val host = "tax.service.gov.uk"

      implicit val req = FakeRequest("GET", s"/?destination=$destination").withHeaders(HeaderNames.HOST -> host)

      val expectedJson = withRemovedWhitespace {
        s"""
           {
             "_links": {
               "self": {
                 "href": "http://$host/file-transfer/envelopes?destination=$destination"
               }
             },
             "_embedded": {
               "envelopes": [
                 {
                   "id": "${envelope._id}",
                   "destination": "${envelope.destination.get}",
                   "application": "${envelope.application.get}",
                   "_embedded": {
                     "files": [
                       {
                         "href": "/file-upload/envelopes/${envelope._id}/files/${file.fileId}/content",
                         "name": "${file.name.get}",
                         "contentType": "${file.contentType.get}",
                         "length": ${file.length.get},
                         "created": "$dateAsText",
                         "_links": {
                           "self": {
                             "href": "/file-upload/envelopes/${envelope._id}/files/${file.fileId}"
                           }
                         }
                       }
                     ]
                   },
                   "_links": {
                     "self": {
                       "href": "/file-transfer/envelopes/${envelope._id}"
                     },
                     "package": {
                       "href": "/file-transfer/envelopes/${envelope._id}",
                       "type": "application/zip"
                     },
                     "files": [
                       {
                         "href": "/files/${file.fileId}"
                       }
                     ]
                   }
                 }
               ]
             }
           }
     """
      }

      val actualJson = Json.toJson(OutputForTransfer(Seq(envelope)))

      withClue("actualJson = " + Json.prettyPrint(actualJson)){
        actualJson.toString shouldBe expectedJson
      }

    }
  }

  def withRemovedWhitespace(s: String) = s.replaceAll("\\s","")

}
