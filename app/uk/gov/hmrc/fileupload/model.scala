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

package uk.gov.hmrc.fileupload

import java.util.UUID

import play.api.libs.json._
import play.api.mvc.PathBindable
import play.utils.UriEncoding
import uk.gov.hmrc.play.binders.SimpleObjectBinder

case class EnvelopeId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}

object EnvelopeId {
  implicit val writes = new Writes[EnvelopeId] {
    def writes(id: EnvelopeId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[EnvelopeId] {
    def reads(json: JsValue): JsResult[EnvelopeId] = json match {
      case JsString(value) => JsSuccess(EnvelopeId(value))
      case _ => JsError("invalid envelopeId")
    }
  }
  implicit val binder: PathBindable[EnvelopeId] =
    new SimpleObjectBinder[EnvelopeId](EnvelopeId.apply, _.value)
}

case class FileId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}

object FileId {
  val charset = "UTF-8"

  implicit val writes = new Writes[FileId] {
    def writes(id: FileId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileId] {
    def reads(json: JsValue): JsResult[FileId] = json match {
      case JsString(value) => JsSuccess(FileId(value))
      case _ => JsError("invalid fileId")
    }
  }
  val binder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](FileId.apply, _.value) // reading is already decoded by routes as parameters

  implicit val urlBinder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](
      str => FileId(UriEncoding.decodePathSegment(str, charset)),
      fId => UriEncoding.encodePathSegment(fId.value, charset) )
}

case class FileRefId(value: String) extends AnyVal {
  override def toString: String = value
}

object FileRefId {
  // UUID was valid only for mongo refs, S3 has different meaning here
  @deprecated("only for test compatibility", "migration issue workaround")
  def apply(): FileRefId = FileRefId(UUID.randomUUID().toString)

  implicit val writes = new Writes[FileRefId] {
    def writes(id: FileRefId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileRefId] {
    def reads(json: JsValue): JsResult[FileRefId] = json match {
      case JsString(value) => JsSuccess(FileRefId(value))
      case _ => JsError("invalid fileId")
    }
  }
  implicit val binder: PathBindable[FileRefId] =
    new SimpleObjectBinder[FileRefId](FileRefId.apply, _.value)
}

case class EventType(value: String) extends AnyVal {
  override def toString: String = value
}
