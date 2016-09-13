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
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

trait EventStore {

  def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork)

  def unitsOfWorkForAggregate(streamId: StreamId): List[UnitOfWork]
}

//class InMemoryEventStore extends EventStore {
//
//  var allEvents = Map.empty[StreamId, List[Event]]
//
//  override def saveEvents(streamId: StreamId, unitOfWork: UnitOfWork, expectedVersion: Version): Unit = {
//    println(s"saveEvent: $events")
//    val currentEvents = allEvents.getOrElse(streamId, List.empty)
//    allEvents = allEvents + (streamId -> currentEvents.++(events))
//    //TODO: publish events after successfully saved
//  }
//
//  override def eventsForAggregate(streamId: StreamId): List[Event] = {
//    val events = allEvents.getOrElse(streamId, List.empty)
//    println(s"eventsForAggregate: $events")
//    events
//  }
//}

class MongoEventStore(mongo: () => DB with DBMetaCommands)
                     (implicit ec: ExecutionContext,
                      reader: BSONDocumentReader[UnitOfWork],
                      writer: BSONDocumentWriter[UnitOfWork]) extends EventStore {

  val collection = mongo().collection[BSONCollection]("events")

  collection.indexesManager.ensure(Index(List("streamId" -> IndexType.Text)))

  override def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Unit = {
    val result = collection.insert(unitOfWork)
    Await.result(result.map(r => r.ok), 5 seconds)
  }

  override def unitsOfWorkForAggregate(streamId: StreamId): List[UnitOfWork] = {
    val result = collection.find(BSONDocument("streamId" -> streamId.value)).cursor[UnitOfWork]().collect[List]()
    Await.result(result, 5 seconds)
  }
}
