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

package uk.gov.hmrc.fileupload.repositories

import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

object DefaultMongoConnection extends MongoDbConnection

object EnvelopeRepository  {
	def apply(mongo: () => DB): EnvelopeRepository = new EnvelopeRepository(mongo)
}

class EnvelopeRepository(mongo: () => DB)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes", mongo, domainFormat = Envelope.envelopeReads ){

  def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] ={
    insert(envelope).map {
	    case wr : WriteResult if wr.ok && wr.n > 0 => true
	    case _ => false
    }
  }

  def get(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
	  find("_id" -> id.stringify).map(_.headOption)
  }

	def delete(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Boolean] = {
		remove("_id" -> id.stringify).map {
			case wr : WriteResult if wr.ok && wr.n > 0 => true
			case _ => false
		}
	}

}
