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

package uk.gov.hmrc.fileupload.actors

import java.util.UUID

import akka.testkit.TestActors
import com.fasterxml.jackson.annotation.ObjectIdGenerators.UUIDGenerator
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsResult, Json}
import reactivemongo.api.commands.DefaultWriteResult
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.models._
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by Josiah on 6/4/2016.
  */
class StorageSpec extends ActorSpec with MockitoSugar {

  import scala.language.postfixOps
  import Support._
  import Storage._
	val envelopeRepository = mock[EnvelopeRepository]
  val storage = system.actorOf(Storage.props(envelopeRepository))

  "A storage" should{
    "Respond with an Envelop when it receives a find by id message" in {
      within(500 millis){
        val optEnvelope = Some(Support.envelope)
        val envelope = optEnvelope.get
	      when(envelopeRepository.get(any())(any())).thenReturn(Future.successful(optEnvelope))
        storage ! FindById(envelope._id)
        expectMsg(optEnvelope)
      }
    }

    "Respond with nothing when it receives a find by id for a non existent id" in {
      val id = UUID.randomUUID().toString
	    when(envelopeRepository.get(any())(any())).thenReturn(Future.successful(None))
      storage ! FindById(id)
      expectMsg(None)
    }

	  "respond with a Id when it receives a create envelope message" in {
		  within(500 millis) {

			  val id = UUID.randomUUID().toString
			  val rawData = Support.envelopeBody.asInstanceOf[JsObject] ++ Json.obj("_id" -> id)
			  val envelope = Json.fromJson[Envelope](rawData).get

			  when(envelopeRepository.add(any())(any())).thenReturn(Future.successful(true))

			  storage ! Save(envelope)

			  expectMsg(id)
		  }
	  }

	  "respond with success true after deleting an Envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.delete(any())(any())).thenReturn(Future.successful(true))
			  storage ! Remove(envelope._id )

			  expectMsg(true)
		  }
	  }

	  "respond with success false when not able to delete an Envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.delete(any())(any())).thenReturn(Future.successful(false))
			  storage ! Remove(envelope._id )

			  expectMsg(false)
		  }
	  }

	  "respond with a success true after adding a file to an envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.addFile(any(), any())(any())).thenReturn(Future.successful(true))
			  storage ! AddFile(envelope._id, fileId = "456" )

			  expectMsg(true)
		  }
	  }

	  "respond with a success false after adding a file to an envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.addFile(any(), any())(any())).thenReturn(Future.successful(false))
			  storage ! AddFile(envelope._id, fileId = "456" )

			  expectMsg(false)
		  }
	  }
	  "respond with a failure when adding of file fails" in {
		  within(500 millis){
			  when(envelopeRepository.addFile(any(), any())(any())).thenReturn(Future.failed(new RuntimeException))
			  storage ! AddFile(envelope._id, fileId = "456" )
			  expectMsgClass(classOf[RuntimeException])
		  }
	  }

	  "respond with a failure when a file is already in the envelope" in {
		  within(500 millis){
			  val envelope = Support.envelope.copy(files = Some(Seq(File(href="test", id = "filestore.txt"))))
			  when(envelopeRepository.addFile(any(), any())(any())).thenReturn(Future.failed(new DuplicateFileException("")))
			  storage ! AddFile(envelope._id, fileId = "456" )
			  expectMsgClass(classOf[DuplicateFileException])
		  }
	  }
	  "respond with a success true after adding file metadata to an envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.addFile(any())(any())).thenReturn(Future.successful(true))
			  storage ! UpdateFile(new FileMetadata())

			  expectMsg(true)
		  }
	  }

	  "respond with a false after an unsuccessful add of a  file metadata to an envelope" in {
		  within(500 millis) {

			  when(envelopeRepository.addFile(any())(any())).thenReturn(Future.successful(false))
			  storage ! UpdateFile(new FileMetadata())

			  expectMsg(false)
		  }
	  }

  }


}
