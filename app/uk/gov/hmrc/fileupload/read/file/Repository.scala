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

package uk.gov.hmrc.fileupload.read.file

import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json._
import play.modules.reactivemongo.GridFSController._
import play.modules.reactivemongo.JSONFileToSave
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.{BSONDateTime, BSONDocument}
import reactivemongo.json._
import uk.gov.hmrc.fileupload._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Repository {
  def apply(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext): Repository = new Repository(mongo)

  sealed trait RetrieveFileError

  object FileNotFoundError extends RetrieveFileError

}

case class FileData(length: Long = 0, data: Enumerator[Array[Byte]] = null)

case class FileInfo(_id: String, chunkSize:Int, length: Long, uploadDate: DateTime)

object FileInfo {
  implicit val dateReads = implicitly[Reads[BSONDateTime]].map(d => new DateTime(d.value))
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileInfoFormat: Format[FileInfo] = Json.format[FileInfo]
}

class Repository(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext) {

  import reactivemongo.json.collection._

  lazy val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](mongo(), "envelopes")

  ensureIndex()

  def ensureIndex() =
    gfs.chunks.indexesManager.ensure(Index(List("files_id" -> Ascending, "n" -> Ascending), unique = true, background = true)).onComplete {
      case Success(result) => Logger.info(s"Index creation for chunks success $result")
      case Failure(t) => Logger.warn(s"Index creation for chunks failed ${ t.getMessage }")
    }

  def iterateeForUpload(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId)
                       (implicit ec: ExecutionContext): Iteratee[ByteStream, Future[JSONReadFile]] = {
    gfs.iteratee(JSONFileToSave(id = Json.toJson(fileRefId.value), filename = None, metadata = Json.obj("envelopeId" -> envelopeId, "fileId" -> fileId)))
  }

  def retrieveFile(_id: FileRefId)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
    gfs.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> _id.value)).headOption.map { file =>
      file.map( f => FileData(f.length, gfs.enumerate(f)))
    }
  }

  def retrieveFileMetaData(fileRefId: FileRefId)(implicit ec: ExecutionContext): Future[Option[FileInfo]] = {
    gfs.files.find(BSONDocument("_id" -> fileRefId.value)).cursor[FileInfo]().collect[List]().map(_.headOption)
  }

  def chunksCount(fileRefId: FileRefId)(implicit ec: ExecutionContext): Future[Int] = {
    gfs.chunks.count(Some(JsObject(Seq("files_id" -> JsString(fileRefId.value)))))
  }

  def recreate(): Unit = {
    Await.result(gfs.files.drop(), 5 seconds)
    Await.result(gfs.chunks.drop(), 5 seconds)
    ensureIndex()
  }
}
