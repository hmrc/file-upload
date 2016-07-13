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

import cats.data.Xor
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Json
import play.modules.reactivemongo.GridFSController._
import play.modules.reactivemongo.JSONFileToSave
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{DB, DBMetaCommands, ReadPreference}
import reactivemongo.bson.BSONDocument
import reactivemongo.json._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.file.Repository.{FileFoundResult, FileNotFoundError, RetrieveFileResult}

import scala.concurrent.{ExecutionContext, Future}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)

  sealed trait RetrieveFileError
  object FileNotFoundError extends RetrieveFileError
  case class FileFoundResult(filename: Option[String] = None, length: Long = 0, data: Enumerator[Array[Byte]] = null)

  type RetrieveFileResult = Xor[RetrieveFileError, FileFoundResult]
}

class Repository(mongo: () => DB with DBMetaCommands) {

  import reactivemongo.json.collection._

  lazy val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](mongo(), "envelopes")

  def addFileMetadata(metadata: FileMetadata)(implicit ec: ExecutionContext): Future[Boolean] = {
    val jsonObj = Json.obj("$set" -> Json.toJson[FileMetadata](metadata))
    gfs.files.update(
      _id(metadata._id),
      jsonObj,
      upsert = true
    ).map(toBoolean)
  }

  def getFileMetadata(compositeFileId: CompositeFileId)(implicit ec: ExecutionContext): Future[Option[FileMetadata]] = {
    import FileMetadata._
    gfs.files.find(_id(compositeFileId))
      .cursor[FileMetadata](ReadPreference.primaryPreferred)
      .headOption
  }

  private def _id(compositeFileId: CompositeFileId) = {
    import FileMetadata._
    Json.obj("_id" -> Json.toJson(compositeFileId))
  }

  def toBoolean(wr: WriteResult): Boolean = wr match {
    case r if r.ok && r.n > 0 => true
    case _ => false
  }

  def iterateeForUpload(compositeFileId: CompositeFileId)(implicit ec: ExecutionContext) : Iteratee[ByteStream, Future[JSONReadFile]] = {
    import FileMetadata._
    gfs.iteratee(JSONFileToSave(filename = None, id = Json.toJson(compositeFileId)))
  }

  def retrieveFile(compositeFileId: CompositeFileId)(implicit ec: ExecutionContext): Future[RetrieveFileResult] = {
    import FileMetadata._
    gfs.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> Json.toJson(compositeFileId))).headOption.map {
      case Some(file: JSONReadFile) => Xor.Right(FileFoundResult(file.filename, file.length, gfs.enumerate(file)))
      case None => Xor.Left(FileNotFoundError)
    }
  }

  def removeAll()(implicit ec: ExecutionContext): Future[List[WriteResult]]  = {
    val files = gfs.files.remove(Json.obj())
    val chunks = gfs.chunks.remove(Json.obj())
    Future.sequence(List(files, chunks))
  }
}
