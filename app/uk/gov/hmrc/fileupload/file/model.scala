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

package uk.gov.hmrc.fileupload.file

import java.util.UUID

import org.joda.time.{DateTime, DateTimeZone}
import play.api.data.validation.ValidationError
import play.api.libs.json._

object FileMetadata {
  implicit val readDate: Reads[DateTime] = new Reads[DateTime]{
    override def reads(json: JsValue): JsResult[DateTime] = json match {
      case JsObject(Seq(("$date", JsNumber(d)))) => JsSuccess(new DateTime(d.toLong, DateTimeZone.UTC))
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.date"))))
    }
  }
  
  implicit val compositeFileIdFormat: Format[CompositeFileId] = Json.format[CompositeFileId]
  implicit val fileMetaDataFormat: Format[FileMetadata] = Json.format[FileMetadata]
}

case class FileMetadata(_id: CompositeFileId = CompositeFileId(envelopeId = UUID.randomUUID().toString, fileId = UUID.randomUUID().toString),
                        name: Option[String] = None,
                        contentType: Option[String] = None,
                        length: Option[Long] = None,
                        uploadDate: Option[DateTime] = None,
                        revision: Option[Int] = None,
                        metadata: Option[JsObject] = None)

case class CompositeFileId(envelopeId: String, fileId: String)
