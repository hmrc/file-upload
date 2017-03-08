/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsObject, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

sealed trait Event

case class FileInQuarantineStored(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId,
                                  created: Long, name: String, fileLength: Long, contentType: String, metadata: JsObject) extends Event

case class FileScanned(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId, hasVirus: Boolean) extends Event

object EventFormatters {
  implicit val fileInQuarantineStoredFormat: Format[FileInQuarantineStored] = Json.format[FileInQuarantineStored]
  implicit val fileScannedFormat: Format[FileScanned] = Json.format[FileScanned]
}
