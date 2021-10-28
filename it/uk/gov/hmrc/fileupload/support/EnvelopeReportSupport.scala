/*
 * Copyright 2021 HM Revenue & Customs
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

object EnvelopeReportSupport {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(s"""
      {
        "callbackUrl": "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
        "metadata": {
          "anything": "the caller wants to add to the envelope"
        }
      }
		""")

  def requestBodyWithConstraints(args: Map[String, Any] = Map.empty) = s"""
       |{
       |  "constraints" : {
       |    "maxSize" : "${ args.getOrElse("maxSize", "12MB") }",
       |    "maxSizePerItem" : "${ args.getOrElse("maxSizePerItem", "10MB") }"
       |  },
       |  "callbackUrl" : "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
       |  "metadata" : {
       |    "anything" : "the caller wants to add to the envelope"
       |  }
       |}
		 """.stripMargin

  def requestBodyWithLowConstraints(args: Map[String, Any] = Map.empty) = s"""
      |{
      |  "constraints" : {
      |    "maxItems" : ${ args.getOrElse("maxItems", 1) },
      |    "maxSize" : "1MB",
      |    "maxSizePerItem" : "1MB"
      |  },
      |  "callbackUrl" : "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
      |  "metadata" : {
      |    "anything" : "the caller wants to add to the envelope"
      |  }
      |}
		 """.stripMargin

}
