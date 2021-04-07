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

package uk.gov.hmrc.fileupload.write.infrastructure

import play.api.libs.json._
import uk.gov.hmrc.fileupload.write.envelope.{EventSerializer => ES}

object UnitOfWorkSerializer {

  private val eventReads: StreamId => Version => Created => Reads[Event]
    = (streamId: StreamId) => (version: Version) => (created: Created) => (event: JsValue) =>
      for {
        eventId <- (event \ "id").validate[String].map(EventId.apply)
        eventType <- (event \ "eventType").validate[String].map(EventType.apply)
        eventData <- (event \ "eventData").validate[JsValue]
      } yield Event(eventId, streamId, version, created, eventType, ES.toEventData(eventType, eventData))

  private val jsonReads = new Reads[UnitOfWork] {
    override def reads(json: JsValue): JsResult[UnitOfWork] = {
      for {
        streamId <- (json \ "_id" \ "streamId").validate[String].map(StreamId.apply)
        version <- (json \ "_id" \ "version").validate[Int].map(Version.apply)
        created <- (json \ "created").validate[Long].map(Created.apply)
        events <- (json \ "events").validate[Seq[Event]](Reads.seq(eventReads(streamId)(version)(created)))
      } yield {
        UnitOfWork(streamId, version, created, events)
      }
    }
  }

  private val jsonWrites: Writes[UnitOfWork] = (unitOfWork: UnitOfWork) => {
    Json.obj(
      "_id" -> Json.obj(
        "streamId" -> unitOfWork.streamId.value,
        "version" -> unitOfWork.version.value
      ),
      "streamId" -> unitOfWork.streamId.value,
      "created" -> unitOfWork.created.value,
      "events" -> JsArray(unitOfWork.events.map { event =>
        Json.obj(
          "id" -> event.eventId.value,
          "eventType" -> event.eventType.value,
          "eventData" -> ES.fromEventData(event.eventData)
        )
      })
    )
  }

  val format: Format[UnitOfWork] = Format(jsonReads, jsonWrites)

}
