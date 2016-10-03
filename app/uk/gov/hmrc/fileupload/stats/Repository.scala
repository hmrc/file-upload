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

package uk.gov.hmrc.fileupload.stats

import play.api.libs.json.{Format, Json}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object InProgressFile {
  implicit val format: Format[InProgressFile] = Json.format[InProgressFile]
}

case class InProgressFile(_id: FileRefId, envelopeId: EnvelopeId, fileId: FileId, startedAt: Long)

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[InProgressFile, BSONObjectID](collectionName = "inprogress-files", mongo, domainFormat = InProgressFile.format) {

  def delete(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove("_id" -> fileRefId) map toBoolean
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }
}