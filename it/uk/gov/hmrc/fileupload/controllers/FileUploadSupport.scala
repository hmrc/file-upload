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

package uk.gov.hmrc.fileupload.controllers

import java.io.File
import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.libs.ws._
import play.api.test.WithServer
import reactivemongo.api.gridfs.GridFS
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.models.{Constraints, Envelope}
import uk.gov.hmrc.fileupload.repositories.{DefaultMongoConnection, EnvelopeRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

/**
  * Created by Josiah on 6/20/2016.
  */
class FileUploadSupport(var mayBeEnvelope: Option[Envelope] = None) extends WithServer{
  import Envelope._

  implicit val ec = ExecutionContext.global

  var self = this
  val url = "http://localhost:9000/file-upload/envelope"
  val repository = new EnvelopeRepository(DefaultMongoConnection.db)

  private val payload = "{}".getBytes

	def createEnnvelope(file: File) = createEnvelope(Source.fromFile(file).mkString)

	def createEnvelope(data: String): Future[WSResponse] = createEnvelope(data.getBytes())

	def createEnvelope(data: Array[Byte]): Future[WSResponse] = {
		WS
			.url(url)
			.withHeaders("Content-Type" -> "application/json")
			.post(data)
	}

  def withEnvelope: Future[FileUploadSupport] = {
    createEnvelope(payload)
      .flatMap{ resp =>
        val id = resp.header("Location").map{ _.split("/").last }.get
        getEnvelopeFor(id)
	        .map{ resp =>
		        val envelope = Json.fromJson[Envelope](resp.json).get
		        self.mayBeEnvelope = Some(envelope)
		        self
	        }
      }
  }

  def getEnvelopeFor(id: String): Future[WSResponse] = {
    WS
      .url(s"$url/$id")
      .get()
  }

  def refresh: Future[FileUploadSupport] = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    getEnvelopeFor(mayBeEnvelope.get._id)
	    .map{ resp =>
		    val envelope = Json.fromJson[Envelope](resp.json).get
		    self.mayBeEnvelope = Some(envelope)
		    self
	    }
  }

  def doUpload(data: Array[Byte], fileId: String): Future[WSResponse] = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    WS
      .url(s"$url/${mayBeEnvelope.get._id}/file/$fileId/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
  }

	def getFile(id: String): Future[ByteStream] = {
		import reactivemongo.json.collection._
		val gfs: JSONGridFS = GridFS[JSONSerializationPack.type](DefaultMongoConnection.db() , "envelopes")
		gfs.find[JsValue, JSONReadFile](Json.obj("_id" -> id)).headOption.flatMap{ mayBeReadFile =>
			mayBeReadFile.map{ readFile =>
				val enumerator = gfs.enumerate(readFile)
				val consumer = Iteratee.consume[ByteStream]()
				enumerator |>>> consumer
			}.get
		}

	}


}
