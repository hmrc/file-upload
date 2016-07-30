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

package uk.gov.hmrc.fileupload.envelope

import reactivemongo.api.commands.WriteResult
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)
}

class Repository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes", mongo, domainFormat = Envelope.envelopeReads) {

  def update(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] =
    delete(envelope._id).flatMap(_ => add(envelope))

  def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] = {
    insert(envelope) map toBoolean
  }

  def get(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
    find("_id" -> id).map(_.headOption)
  }

  def delete(id: EnvelopeId)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove("_id" -> id) map toBoolean
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }
}
