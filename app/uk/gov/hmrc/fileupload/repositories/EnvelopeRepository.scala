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

import java.util.UUID

import play.api.Play
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsValue, JsObject, Json}
import play.modules.reactivemongo.{JSONFileToSave, MongoDbConnection}
import reactivemongo.api.{ReadPreference, DB, DBMetaCommands}
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.gridfs.GridFS
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models._
import uk.gov.hmrc.mongo.ReactiveRepository
import play.modules.reactivemongo.GridFSController._
import reactivemongo.json._

import scala.concurrent.{Await, ExecutionContext, Future}

object DefaultMongoConnection extends MongoDbConnection

object EnvelopeRepository  {
	def apply(mongo: () => DB with DBMetaCommands): EnvelopeRepository = new EnvelopeRepository(mongo)
}

class EnvelopeRepository(mongo: () => DB with DBMetaCommands)
  extends ReactiveRepository[Envelope, BSONObjectID](collectionName = "envelopes", mongo, domainFormat = Envelope.envelopeReads ){

	import reactivemongo.json.collection._
	lazy val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](mongo(), "envelopes")

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
        val newFile: Seq[File] = Seq(File(href = uk.gov.hmrc.fileupload.controllers.routes.FileController.upload(envelopeId, fileId).url, id = fileId))

        val updatedEnvelope = envelope.files match {
          case None => envelope.copy(files = Some(newFile))
					case Some(seq) => envelope.copy(files = Some(seq ++ newFile))
				}

				delete(envelopeId).flatMap {    // FIXME api provides no update method
					case true => add(updatedEnvelope)
					case false => Future.successful(false)
				}
			}
			case None => Future.failed(new EnvelopeNotFoundException(envelopeId))
		}
	}

	def addFile(metadata: FileMetadata)(implicit ec: ExecutionContext): Future[Boolean] = {
		import FileMetadata._

		val jsonObj = Json.obj("$set" -> Json.toJson[FileMetadata](metadata))
		gfs.files.update(
			_id(metadata._id),
			jsonObj,
			upsert = true
		).map(toBoolean)
	}

	def getFileMetadata(id: String)(implicit ec: ExecutionContext): Future[Option[FileMetadata]] = {
		import FileMetadata._
		gfs.files.find(_id(id))
			.cursor[FileMetadata](ReadPreference.primaryPreferred)
			.headOption
	}

	private def _id(id: String) = Json.obj("_id" -> id)

	def toBoolean(wr: WriteResult): Boolean = wr match {
		case r if r.ok && r.n > 0 => true
		case _ => false
	}

	def iterateeForUpload(fileId: String)(implicit ec: ExecutionContext) : Iteratee[ByteStream, Future[JSONReadFile]] = {
		gfs.iteratee(JSONFileToSave(filename = None, id = Json.toJson(fileId)))
	}
}
