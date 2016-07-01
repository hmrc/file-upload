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

import akka.util.Timeout
import play.api.http.Status
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits, WithServer}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.models.{FileMetadata, Envelope}
import uk.gov.hmrc.fileupload.repositories.{DefaultMongoConnection, EnvelopeRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal

/**
  * Created by Josiah on 6/20/2016.
  */
class FileUploadSupport(var mayBeEnvelope: Option[Envelope] = None) extends WithServer
	with FutureAwaits with DefaultAwaitTimeout with Status {
  import Envelope._

  implicit val ec = ExecutionContext.global
	implicit val timeout = Timeout(1 minute)
  var self = this
  val url = "http://localhost:9000/file-upload"
  val repository = new EnvelopeRepository(DefaultMongoConnection.db)

  private val payload = "{}".getBytes

	def createEnvelope(file: File): Future[WSResponse] = createEnvelope(Source.fromFile(file).mkString)

	def createEnvelope(data: String): Future[WSResponse] = createEnvelope(data.getBytes())

	def envelope = mayBeEnvelope.get

	def createEnvelope(data: Array[Byte]): Future[WSResponse] = {
		WS
			.url(s"$url/envelope")
			.withHeaders("Content-Type" -> "application/json")
			.post(data)
	}

	def appIsAlive: Boolean = {
		await(WS
			.url(s"$url/heart/beat")
			.get().map{
				case resp if resp.status == OK => true
				case _ => false
			}.recover{ case NonFatal(_) => false }
		)
	}

  lazy val withEnvelope: FileUploadSupport = {
    await(createEnvelope(payload)
      .flatMap{ resp =>
        val id = resp.header("Location").map{ _.split("/").last }.get
        getEnvelopeFor(id)
	        .map{ resp =>
		        val envelope = Json.fromJson[Envelope](resp.json).get
		        self.mayBeEnvelope = Some(envelope)
		        self
	        }
      })
  }

  def getEnvelopeFor(id: String): Future[WSResponse] = {
    WS
      .url(s"$url/envelope/$id")
      .get()
  }

  def refresh: FileUploadSupport = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    await(getEnvelopeFor(mayBeEnvelope.get._id)
	    .map{ resp =>
		    val envelope = Json.fromJson[Envelope](resp.json).get
		    self.mayBeEnvelope = Some(envelope)
		    self
	    })
  }

  def doUpload(data: Array[Byte], fileId: String): WSResponse = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    await(WS
      .url(s"$url/envelope/${envelope._id}/file/$fileId/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data))
  }

	def putFileMetadata(data: String, fileId: String): WSResponse = putFileMetadata(data.getBytes, fileId)

	def putFileMetadata(data: Array[Byte], fileId: String): WSResponse = {
		require(mayBeEnvelope.isDefined, "No envelope defined")
		await(WS
			.url(s"$url/envelope/${envelope._id}/file/$fileId/metadata" )
			.withHeaders("Content-Type" -> "application/json")
			.put(data))
	}


	def getFileMetadataFor(fileId: String, envelopeId: String = envelope._id): FileMetadata = {
		await(WS
		  .url(s"$url/envelope/$envelopeId/file/$fileId/metadata")
			.get()
		  .map(resp => Json.fromJson[FileMetadata](resp.json).get))
	}

	def getFile(id: String): Future[ByteStream] = ???

}
