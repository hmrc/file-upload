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

package uk.gov.hmrc.fileupload.write.infrastructure

import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

trait EventStore {

  def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork)

  def unitsOfWorkForAggregate(streamId: StreamId): List[UnitOfWork]
}

class MongoEventStore(mongo: () => DB with DBMetaCommands)
                     (implicit ec: ExecutionContext,
                      reader: BSONDocumentReader[UnitOfWork],
                      writer: BSONDocumentWriter[UnitOfWork]) extends EventStore {

  val collection = mongo().collection[BSONCollection]("events")

  collection.indexesManager.ensure(Index(List("streamId" -> IndexType.Hashed)))

  override def saveUnitOfWork(streamId: StreamId, unitOfWork: UnitOfWork): Unit = {
    val result = collection.insert(unitOfWork)
    Await.result(result.map(r => r.ok), 5 seconds)
  }

  override def unitsOfWorkForAggregate(streamId: StreamId): List[UnitOfWork] = {
    val result = collection.find(BSONDocument("streamId" -> streamId.value)).cursor[UnitOfWork]().collect[List]()
    Await.result(result, 5 seconds)
  }
}
