/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.write.infrastructure

import play.api.libs.json._
import play.api.mvc.PathBindable
import uk.gov.hmrc.fileupload.SimpleObjectBinder

case class UnitOfWork(
  streamId: StreamId,
  version : Version,
  created : Created,
  events  : Seq[Event]
)

case class Event(
  eventId  : EventId,
  streamId : StreamId,
  version  : Version,
  created  : Created,
  eventType: EventType,
  eventData: EventData
)

case class EventId(value: String) extends AnyVal {
  override def toString = value.toString
}

case class StreamId(value: String) extends AnyVal {
  override def toString = value.toString
}

object StreamId {
  implicit val binder: PathBindable[StreamId] =
    new SimpleObjectBinder[StreamId](StreamId.apply, _.value)
}

case class Version(value: Int) extends AnyVal {
  override def toString = value.toString

  def nextVersion() =
    Version(value + 1)
}

object Version {
  implicit val writes = new Writes[Version] {
    def writes(id: Version): JsValue = JsNumber(id.value)
  }
  implicit val reads = new Reads[Version] {
    def reads(json: JsValue): JsResult[Version] = json match {
      case JsNumber(value) => JsSuccess(Version(value.toInt))
      case _               => JsError("invalid envelopeId")
    }
  }
}

case class Created(value: Long) extends AnyVal {
  override def toString = value.toString
}

case class EventType(value: String) extends AnyVal {
  override def toString = value
}

trait EventData {
  def streamId: StreamId
}

trait Command {
  def streamId: StreamId
}

case object CommandAccepted

trait CommandNotAccepted
case class CommandError(message: String) extends CommandNotAccepted
case class VersionConflict(version: Version, command: Command) extends CommandNotAccepted
