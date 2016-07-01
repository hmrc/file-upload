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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import uk.gov.hmrc.fileupload.actors.EnvelopeService._
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeNotFoundException, FileMetadata, Sealed}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern._

import scala.language.postfixOps
import scala.util.{Failure, Success}

object EnvelopeService {

  case class GetEnvelope(id: String)
  case class NewEnvelope(envelope: Envelope)
  case class DeleteEnvelope(id: String)
  case class SealEnvelope(id: String)
  case class UpdateEnvelope(envelopeId: String, fileId: String)
  case class UpdateFileMetaData(envelopeId: String, data: FileMetadata)
  case class GetFileMetaData(id: String)

  def props(storage: ActorRef, maxTTL: Int): Props =
    Props(classOf[EnvelopeService], storage, maxTTL)
}

class EnvelopeService(storage: ActorRef, maxTTL: Int) extends Actor with ActorLogging {

  import Storage._
	import uk.gov.hmrc.fileupload.actors.Implicits.FutureUtil

  implicit val ex: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(2 seconds)

  override def preStart() {
    log.info("Envelope manager online")
    super.preStart()
  }

  def receive = {
    case GetEnvelope(id) => getEnvelopeFor(id, sender)
    case NewEnvelope(envelope: Envelope) => createEnvelopeFrom(envelope, sender)
    case DeleteEnvelope(id) => deleteEnvelope(id, sender)
    case UpdateEnvelope(envelopeId, fileId) => updateEnvelope(envelopeId, fileId, sender)
    case SealEnvelope(envelopeId) => sealEnvelope(envelopeId, sender)
    case UpdateFileMetaData(envelopeId, data) =>
      // TODO need to update the envelope as well
      storage forward UpdateFile(data)
    case it @ GetFileMetaData(_) => storage forward it
  }

  def getEnvelopeFor(id: String, sender: ActorRef): Unit = {
    (storage ? FindById(id))
      .breakOnFailure
      .onComplete {
        case Success(None) => sender ! new EnvelopeNotFoundException(id)
        case Success(Some(envelope)) => sender ! envelope
        case Failure(t) => sender ! t
      }
  }

  def createEnvelopeFrom(envelope: Envelope, sender: ActorRef): Unit = {
    log.info(s"processing CreateEnvelope")
    storage forward Save(envelope)
  }

  def deleteEnvelope(id: String, sender: ActorRef): Unit = {
    storage ask FindById(id) onSuccess {
      case Some(envelope: Envelope) if envelope.isSealed() =>
        log.info(s"envelope ${envelope._id} is sealed. Cannot delete.")
        sender ! new EnvelopeSealedException(envelope)
      case Some(envelope: Envelope) =>
        log.info(s"deleting envelope ${envelope._id}")
        storage ask Remove(id) onComplete {
          case response =>
            log.info(s"sending response $response")
            sender ! response
        }
      case None =>
        log.info(s"envelope $id not found")
        sender ! Success(false)
      case Failure(t) => sender ! t
    }

  }

  def updateEnvelope(id: String, fileId: String, sender: ActorRef): Unit = {
    storage ask FindById(id) onSuccess {
      case Some(envelope: Envelope) if envelope.isSealed() =>
        log.info(s"envelope ${envelope._id} is sealed. Cannot update.")
        sender ! new EnvelopeSealedException(envelope)
      case Some(envelope: Envelope) =>
        log.info(s"forwarding add file message from $sender to storage")
        // FIXME we can't just forward this request to the storage
        // FIXME we have to wait on the storage and then rollback the fileupload
        // FIXME on failure
        storage ask AddFile(id, fileId) onComplete {
          case response =>
            log.info(s"sending response $response")
            sender ! response
        }
      case None =>
        log.info(s"envelope $id not found")
        sender ! Success(false)
      case Failure(t) => sender ! t
    }
  }

  def sealEnvelope(id: String, sender: ActorRef): Unit = {
    storage ask FindById(id) onSuccess {
      case Some(envelope: Envelope) if envelope.isSealed() =>
        log.info(s"envelope ${envelope._id} is already sealed")
        sender ! new EnvelopeSealedException(envelope)
      case Some(envelope: Envelope) =>
        val sealedEnvelope: Envelope = envelope.copy(status = Sealed)
        storage ask Save(sealedEnvelope) onComplete {
          case response =>
            log.info(s"sending response $response")
            sender ! response
        }
      case None =>
        log.info(s"envelope $id not found")
        sender ! Success(false)
      case Failure(t) => sender ! t
    }
  }


  override def postStop() {
    log.info("Envelope storage is going offline")
    super.postStop()
  }

}
