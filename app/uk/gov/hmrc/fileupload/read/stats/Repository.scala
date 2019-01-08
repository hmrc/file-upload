/*
 * Copyright 2019 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

object InProgressFile {
  implicit val format: Format[InProgressFile] = Json.format[InProgressFile]
}

case class InProgressFile(_id: FileRefId, envelopeId: EnvelopeId, fileId: FileId, startedAt: Long)

object Repository {
  def apply(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext)
  extends ReactiveRepository[InProgressFile, BSONObjectID](collectionName = "inprogress-files", mongo, domainFormat = InProgressFile.format) {

  def delete(envelopeId: EnvelopeId, fileId: FileId): Future[Boolean] =
    remove("envelopeId" -> envelopeId, "fileId" -> fileId) map toBoolean

  def deleteAllInAnEnvelop(envelopeId: EnvelopeId): Future[Boolean] =
    remove("envelopeId" -> envelopeId) map toBoolean

  def deleteByFileRefId(fileRefId: FileRefId)(implicit ec: ExecutionContext): Future[Boolean] =
    remove("_id" -> fileRefId).map(toBoolean)

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }

  def all(): Future[List[InProgressFile]] = findAll()

  def findByEnvelopeId(envelopeId: EnvelopeId): Future[List[InProgressFile]] = find("envelopeId" -> envelopeId)

  def recreate()(implicit ec: ExecutionContext): Unit =
    Await.result(drop(ec), 5 seconds)
}
