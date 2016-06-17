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

import play.api.libs.iteratee.Iteratee
import play.modules.reactivemongo.{JSONFileToSave, MongoDbConnection}
import reactivemongo.api.{DBMetaCommands, DB}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.gridfs.GridFS
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models.{EnvelopeNotFoundException, Envelope}
import uk.gov.hmrc.mongo.ReactiveRepository
import play.modules.reactivemongo.GridFSController._
import reactivemongo.json._
import scala.concurrent.{ExecutionContext, Future}

object DefaultMongoConnection extends MongoDbConnection

object EnvelopeRepository  {
	def apply(mongo: () => DB with DBMetaCommands): EnvelopeRepository = new EnvelopeRepository(mongo)
}

class EnvelopeRepository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes", mongo, domainFormat = Envelope.envelopeReads ){


	def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] ={
    insert(envelope) map toBoolean
  }

  def get(id: String)(implicit ec: ExecutionContext): Future[Option[Envelope]] = {
	  find("_id" -> id).map(_.headOption)
  }

	def delete(id: String)(implicit ec: ExecutionContext): Future[Boolean] = {
		remove("_id" -> id) map toBoolean
	}

	def addFile(envelopeId: String, fileId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
		get(envelopeId).flatMap {
			case Some(envelope) => {
				val update = envelope.files match {
					case None => envelope.copy(files = Some(Seq(fileId)))
					case Some(seq) => envelope.copy(files = Some(seq ++ Seq(fileId)))
				}
				delete(envelopeId).flatMap {    // FIXME api provides no update method
					case true => add(update)
					case false => Future.successful(false)
				}
			}
			case None => Future.failed(new EnvelopeNotFoundException(envelopeId))
		}

	}


	def toBoolean(wr: WriteResult): Boolean = wr match {
		case wr : WriteResult if wr.ok && wr.n > 0 => true
		case _ => false
	}

	def iterateeForUpload(file: String)(implicit ec: ExecutionContext) : Iteratee[ByteStream, Future[JSONReadFile]] = {
		import reactivemongo.json.collection._
		val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](mongo(), "envelopes")
		gfs.iteratee(JSONFileToSave(Some(file)))
	}
}
