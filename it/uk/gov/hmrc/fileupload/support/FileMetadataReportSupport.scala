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

package uk.gov.hmrc.fileupload.support

import play.api.libs.json.{Json, JsValue}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

object FileMetadataReportSupport {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(s"""
      {
        "name":"${args.getOrElse("name", "test.jpg")}",
        "contentType":"${args.getOrElse("contentType", "application/pdf")}",
        "metadata":{
          "id":"${args.getOrElse("metadata.id", "1234567890")}",
          "origin":{
            "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
            "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
            "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
            "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
            "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}",
            "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "10.0.0.1")}"
          },
          "sender":{
            "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
          }
        }
      }
		""")

  def responseBodyAsJson(envelopeId: EnvelopeId, fileId: FileId, args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(s"""
      {
        "id":"$fileId",
        "status":"QUARANTINED",
        "name":"${args.getOrElse("name", "test.jpg")}",
        "contentType":"${args.getOrElse("contentType", "application/pdf")}",
        "length" : 123,
        "created":"1970-01-01T00:00:00Z",
        "metadata":{
          "sender":{
            "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
          },
          "origin":{
            "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
            "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
            "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
            "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
            "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "10.0.0.1")}",
            "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}"
          },
          "id":"${args.getOrElse("metadata.id", "1234567890")}"
        },
        "href" : "/file-upload/envelopes/$envelopeId/files/$fileId/content"
      }
    """)

  def responseBodyWithFileInfoAsJson(id: String, args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(s"""
      {
        "id":"$id",
        "name":"${args.getOrElse("name", "test.jpg")}",
        "contentType":"${args.getOrElse("contentType", "application/pdf")}",
        "length":${args.getOrElse("length", 0)},
        "created":"${args.getOrElse("created", "")}",
        "metadata":{
          "id":"${args.getOrElse("metadata.id", "1234567890")}",
          "origin":{
            "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
            "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
            "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
            "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
            "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}",
            "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "10.0.0.1")}"
          },
          "sender":{
            "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
          }
        }
      }
		""")
}
