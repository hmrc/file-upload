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

package uk.gov.hmrc.fileupload

import java.util.UUID

import play.api.libs.json._
import play.api.mvc.PathBindable

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
  implicit val writes = new Writes[FileId] {
    def writes(id: FileId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileId] {
    def reads(json: JsValue): JsResult[FileId] = json match {
      case JsString(value) => JsSuccess(FileId(value))
      case _ => JsError("invalid fileId")
    }
  }

  implicit val urlBinder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](
      FileId.apply, // decoding is not consistent, done by play for all endpoints parameters
      _.value )
}

case class FileRefId(value: String) extends AnyVal {
  override def toString: String = value
}

object FileRefId {
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
