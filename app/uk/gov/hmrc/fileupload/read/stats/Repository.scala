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

package uk.gov.hmrc.fileupload.read.stats

import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.model._
import org.mongodb.scala.result.DeleteResult
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.{MongoComponent, MongoComment}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object InProgressFile {
  given format: Format[InProgressFile] = Json.format[InProgressFile]
}

case class InProgressFile(
  _id       : FileRefId,
  envelopeId: EnvelopeId,
  fileId    : FileId,
  startedAt : Long
)

class Repository(
  mongoComponent: MongoComponent
)(using
  ExecutionContext
) extends PlayMongoRepository[InProgressFile](
  collectionName = "inprogress-files",
  mongoComponent = mongoComponent,
  domainFormat   = InProgressFile.format,
  indexes        = IndexModel(Indexes.ascending("envelopeId")) ::
                   IndexModel(Indexes.ascending("startedAt"))  ::
                   Nil
) {

  def insert(inProgressFile: InProgressFile): Future[Boolean] =
    collection.insertOne(inProgressFile).toFuture().map(_.wasAcknowledged())

  def delete(envelopeId: EnvelopeId, fileId: FileId): Future[Boolean] =
    collection
      .deleteMany(filter = Filters.and(Filters.equal("envelopeId", envelopeId.value), Filters.equal("fileId", fileId.value)))
      .toFuture().map(toBoolean)

  def deleteAllInAnEnvelop(envelopeId: EnvelopeId): Future[Boolean] =
    collection.deleteMany(filter = Filters.equal("envelopeId", envelopeId.value)).toFuture().map(toBoolean)

  def deleteByFileRefId(fileRefId: FileRefId)(using ExecutionContext): Future[Boolean] =
    collection.deleteMany(filter = Filters.equal("_id", fileRefId.value)).toFuture().map(toBoolean)

  private def toBoolean(wr: DeleteResult): Boolean =
    wr.getDeletedCount > 0

  def all(): Future[List[InProgressFile]] =
    collection
      .find()
      .sort(Sorts.descending("startedAt"))
      .comment(MongoComment.NoIndexRequired)
      .toFuture().map(_.toList)

  def statsAddedSince(start: Instant): Future[Long] =
    collection.countDocuments(Filters.gt("startedAt", start.toEpochMilli)).toFuture()

  def findByEnvelopeId(envelopeId: EnvelopeId): Future[List[InProgressFile]] =
    collection.find(filter = Filters.equal("envelopeId", envelopeId.value)).toFuture().map(_.toList)

  def recreate()(using ec: ExecutionContext): Unit =
    Await.result(collection.drop().toFuture().map(_ => true)(ec), 5.seconds)
}
