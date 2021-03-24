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

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}

object EventsSupport extends Support {

  def fileInQuarantineStoredRequestBodyAsJson(e: FileInQuarantineStored) = Json.parse(fileInQuarantineStoredRequestBody(e))

  def fileInQuarantineStoredRequestBody(e: FileInQuarantineStored) =
    s"""
       |{
       |  "envelopeId": "${e.envelopeId.value}",
       |	"fileId": "${e.fileId.value}",
       |	"fileRefId": "${e.fileRefId.value}",
       |	"created": ${e.created},
       |	"name": "${e.name}",
       |	"contentType": "${e.contentType}",
       |  "fileLength": ${e.fileLength.get},
       |	"metadata": ${Json.stringify(e.metadata)}
       |}
		 """.stripMargin

  def fileScannedRequestBodyAsJson(e: FileScanned) = Json.parse(fileScannedRequestBody(e))

  def fileScannedRequestBody(e: FileScanned) =
    s"""
       |{
       |  "envelopeId": "${e.envelopeId}",
       |	"fileId": "${e.fileId}",
       |	"fileRefId": "${e.fileRefId}",
       |	"hasVirus": ${e.hasVirus}
       |}
		 """.stripMargin
}
