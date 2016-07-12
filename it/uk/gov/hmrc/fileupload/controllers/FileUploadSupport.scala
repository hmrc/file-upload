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

import play.api.libs.json.Json
import play.api.libs.ws._
import uk.gov.hmrc.fileupload._

import scala.concurrent.Future
import scala.language.postfixOps

/**
	* Created by Josiah on 6/20/2016.
	*/
class FileUploadSupport(var mayBeEnvelope: Option[EnvelopeReport] = None) extends ITestSupport with EnvelopeActions{
  import play.api.Play.current

	var self = this
	private val payload = "{}".getBytes

	def envelope = mayBeEnvelope.get

//	lazy val withEnvelope: FileUploadSupport = {
//		val resp = createEnvelope(payload)
//    val id = resp.header("Location").map{ _.split("/").last }.get
//    getEnvelopeFor(id).map { resp =>
//        val envelope = Json.fromJson[EnvelopeReport](resp.json).get
//        self.mayBeEnvelope = Some(envelope)
//        self
//    }
//	}

	def refresh: FileUploadSupport = {
		require(mayBeEnvelope.isDefined, "No envelope defined")
		await(getEnvelopeFor(mayBeEnvelope.get.id.get)
			.map{ resp =>
				val envelope = Json.fromJson[EnvelopeReport](resp.json).get
				self.mayBeEnvelope = Some(envelope)
				self
			})
	}

	def doUpload(data: Array[Byte], fileId: String): WSResponse = {
		require(mayBeEnvelope.isDefined, "No envelope defined")
		await(WS
			.url(s"$url/envelope/${envelope.id.get}/file/$fileId/content")
			.withHeaders("Content-Type" -> "application/octet-stream")
			.put(data))
	}

	def putFileMetadata(data: String, fileId: String): WSResponse = putFileMetadata(data.getBytes, fileId)

	def putFileMetadata(data: Array[Byte], fileId: String): WSResponse = {
		require(mayBeEnvelope.isDefined, "No envelope defined")
		await(WS
			.url(s"$url/envelope/${envelope.id.get}/file/$fileId/metadata" )
			.withHeaders("Content-Type" -> "application/json")
			.put(data))
	}


	def getFileMetadataFor(fileId: String, envelopeId: String = envelope.id.get): String =
		await(WS
			.url(s"$url/envelope/$envelopeId/file/$fileId/metadata")
			.get()
			.map(_.body))


	def getFile(id: String): Future[ByteStream] = ???

}
