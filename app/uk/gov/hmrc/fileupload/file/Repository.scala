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

package uk.gov.hmrc.fileupload.file

import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Json
import play.modules.reactivemongo.GridFSController._
import play.modules.reactivemongo.JSONFileToSave
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONDocument
import reactivemongo.json._
import uk.gov.hmrc.fileupload._

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)

  sealed trait RetrieveFileError

  object FileNotFoundError extends RetrieveFileError
}

case class FileData(length: Long = 0, data: Enumerator[Array[Byte]] = null)

class Repository(mongo: () => DB with DBMetaCommands) {

  import reactivemongo.json.collection._

  lazy val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](mongo(), "envelopes")

  def iterateeForUpload(envelopeId: EnvelopeId, fileId: FileId)(implicit ec: ExecutionContext): Iteratee[ByteStream, Future[JSONReadFile]] = {
    gfs.iteratee(JSONFileToSave(filename = None, metadata = Json.obj("envelopeId" -> envelopeId, "fileId" -> fileId)))
  }

  def retrieveFile(_id: FileId)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
    gfs.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> _id.value)).headOption.map { file =>
      file.map( f => FileData(f.length, gfs.enumerate(f)))
    }
  }

  def removeAll()(implicit ec: ExecutionContext): Future[List[WriteResult]] = {
    val files = gfs.files.remove(Json.obj())
    val chunks = gfs.chunks.remove(Json.obj())
    Future.sequence(List(files, chunks))
  }
}
