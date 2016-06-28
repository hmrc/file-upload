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

import java.util.{NoSuchElementException, UUID}

import akka.testkit.TestActorRef
import akka.util.Timeout
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeNotFoundException, FileMetadata}
import uk.gov.hmrc.fileupload.actors.Storage.{AddFile, FindById, Remove, Save}
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeNotFoundException, Sealed, ValidationException}

import scala.concurrent.duration._
import scala.util.{Failure, Try}

/**
  * Created by Josiah on 6/3/2016.
  */
class EnvelopeServiceSpec extends ActorSpec {

  import EnvelopeService._

  import scala.language.postfixOps
  val MAX_TIME_TO_LIVE = 2

  val storage = TestActorRef[ActorStub]
  val marshaller = TestActorRef[ActorStub]
  val envelopService = system.actorOf(EnvelopeService.props(storage, marshaller, MAX_TIME_TO_LIVE))
  implicit val ec = system.dispatcher


  "An EnvelopeService" should {
    "respond with an envelope when it receives a GetEnvelop message" in {
      within(timeout) {
        val envelope = Support.envelope
        val id = envelope._id

        storage.underlyingActor.setReply(Some(envelope))

        envelopService ! GetEnvelope(id)
        expectMsg(envelope)
      }
    }

    "respond with a BadRequestException when it receives a GetEnvelope message with an invalid id" in {
      within(timeout) {
        val id = UUID.randomUUID().toString

        storage.underlyingActor.setReply(None)

        envelopService ! GetEnvelope(id)
        expectMsg(new EnvelopeNotFoundException(id))
      }
    }
  }

  "An EnvelopeService" should {
    "respond with true when it receives a CreateEnvelope message" in {
      within(timeout) {
        val envelope = Support.envelope
        storage.underlyingActor.setReply(true)

        envelopService ! NewEnvelope(envelope)

        expectMsg(true)
      }
    }


    "respond with Success after deleting an envelope" in {
      within(timeout) {
        storage.underlyingActor.setReceive((sender) => {
          case FindById(id) => sender ! Some(Support.envelope)
          case Remove(id) => sender ! true
        })
        envelopService ! DeleteEnvelope(Support.envelope._id)
        expectMsg(Success(true))
      }
    }

    "update an envelope to add a new file" in {
      within(timeout) {
        storage.underlyingActor.setReceive((sender) => {
          case FindById(id) => sender ! Some(Support.envelope)
          case AddFile(envelopeId, fileId) => sender ! true
        })
        envelopService ! UpdateEnvelope(envelopeId = "123", fileId = "456")
        expectMsg(Success(true))
      }
    }

    "respond with exception after trying to delete a sealed envelope" in {
      within(timeout) {
        val envelope: Envelope = Support.envelope.copy(status = Sealed)

        storage.underlyingActor.setReceive((sender) => {
          case FindById(id) => sender ! Some(envelope)
        })
        envelopService ! DeleteEnvelope(envelope._id)
        expectMsg(new EnvelopeSealedException(envelope))
      }
    }

    "respond with an exception after trying to add a new file to a sealed envelope" in {
      within(timeout) {
        val envelope: Envelope = Support.envelope.copy(status = Sealed)

        storage.underlyingActor.setReceive((sender) => {
          case FindById(id) => sender ! Some(envelope)
          case AddFile(envelopeId, fileId) => sender ! true
        })
        envelopService ! UpdateEnvelope(envelopeId = "123", fileId = "456")
        expectMsg(new EnvelopeSealedException(envelope))
      }
    }


    "update an envelope to add a new file" in {
      within(timeout){
        storage.underlyingActor.setReply(true)
        envelopService ! UpdateEnvelope(envelopeId = "123", fileId = "456")
        expectMsg(true)
      }
    }
    "add metadata to file when envelope exist" in {
      within(timeout){
        val envelopeId = nextId()
        val data = FileMetadata(_id = nextId())
        storage.underlyingActor.setReply(true)
        envelopService ! UpdateFileMetaData(envelopeId, data)
        expectMsg(true)
      }
    }
    "return a file metadata for the requested id" in {
      within(timeout){
        val fileMetadata = FileMetadata(_id = nextId())
        storage.underlyingActor.setReply(Some(fileMetadata))
        envelopService ! GetFileMetaData(fileMetadata._id)
        expectMsg(Some(fileMetadata))
      }
    }
  }

}

	import scala.language.implicitConversions
	implicit def timeoutToDuration(timeout: Timeout): FiniteDuration = timeout.duration
}
