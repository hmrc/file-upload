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

package uk.gov.hmrc.fileupload.domain

import play.api.libs.json.JsValue
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONObjectID}
import reactivemongo.json.BSONFormats

object EventSerializer {

  class EventReader(toEventData: (EventType, JsValue) => EventData) extends BSONDocumentReader[Event] {
    def read(doc: BSONDocument): Event = {
      val eventId = EventId(doc.getAs[String]("_id").get)
      val streamId = StreamId(doc.getAs[String]("streamId").get)
      val version = Version(doc.getAs[Int]("version").get)
      val created = Created(doc.getAs[Long]("created").get)
      val eventType = EventType(doc.getAs[String]("eventType").get)
      val eventData = toEventData(eventType, BSONFormats.toJSON(doc.get("eventData").get))

      Event(eventId, streamId, version, created, eventType, eventData)
    }
  }

  class EventWriter(fromEventData: EventData => JsValue) extends BSONDocumentWriter[Event] {
    override def write(t: Event): BSONDocument = BSONDocument(
      "_id" -> t.eventId.value,
      "streamId" -> t.streamId.value,
      "version" -> t.version.value,
      "created" -> t.created.value,
      "eventType" -> t.eventType.value,
      "eventData" -> BSONFormats.toBSON(fromEventData(t.eventData)).get
    )
  }

}
