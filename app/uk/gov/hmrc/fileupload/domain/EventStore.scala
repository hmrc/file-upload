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

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

trait EventStore {

  def saveEvents(streamId: StreamId, events: List[Event], expectedVersion: Version)

  def eventsForAggregate(streamId: StreamId): List[Event]
}

class InMemoryEventStore extends EventStore {

  var allEvents = Map.empty[StreamId, List[Event]]

  override def saveEvents(streamId: StreamId, events: List[Event], expectedVersion: Version): Unit = {
    println(s"saveEvent: $events")
    val currentEvents = allEvents.getOrElse(streamId, List.empty)
    allEvents = allEvents + (streamId -> currentEvents.++(events))
    //TODO: publish events after successfully saved
  }

  override def eventsForAggregate(streamId: StreamId): List[Event] = {
    val events = allEvents.getOrElse(streamId, List.empty)
    println(s"eventsForAggregate: $events")
    events
  }
}

class MongoEventStore(mongo: () => DB with DBMetaCommands)
                     (implicit ec: ExecutionContext,
                      reader: BSONDocumentReader[Event],
                      writer: BSONDocumentWriter[Event]) extends EventStore {

  val collection = mongo().collection[BSONCollection]("events")

  override def saveEvents(streamId: StreamId, events: List[Event], expectedVersion: Version): Unit = {
    val bulkDocs = events.map(implicitly[collection.ImplicitlyDocumentProducer](_))
    val result = collection.bulkInsert(ordered = true)(bulkDocs: _*)

    Await.result(result.map(r => r.ok), 5 seconds)
  }

  override def eventsForAggregate(streamId: StreamId): List[Event] = {
    val result = collection.find(BSONDocument("streamId" -> streamId.value)).cursor[Event]().collect[List]()
    Await.result(result, 5 seconds)
  }
}
