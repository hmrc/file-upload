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

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import uk.gov.hmrc.fileupload.models.{FileMetadata, Envelope}
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.util.{Try, Failure, Success}

object Storage{
  case class FindById(id: String)
	case class Save(envelope: Envelope)
	case class Remove(id: String)
	case class AddFile(envelopeId: String, fileId: String)
	case class UpdateFile(data: FileMetadata)

  def props(envelopeRepository: EnvelopeRepository): Props = Props(classOf[Storage], envelopeRepository)

}

class Storage(val envelopeRepository: EnvelopeRepository) extends Actor with ActorLogging{
  import Storage._
	import EnvelopeService._

  implicit val ex: ExecutionContext = context.dispatcher

	override def preStart(): Unit = {
    log.info("Envelope storage online")
		super.preStart()
  }

	def receive = {
    case FindById(id) => findEnvelopeById(id, sender)
    case Save(envelope) => save(envelope, sender)
    case Remove(id) => remove(id, sender)
    case AddFile(envelopeId, fileId) => addFile(envelopeId, fileId, sender)
		case UpdateFile(metadata) => addFile(metadata, sender)
    case GetFileMetaData(id) => getFileMetadata(id, sender)
  }


	def save(envelope: Envelope, sender: ActorRef): Unit = {
		envelopeRepository.update(envelope).onComplete {
				case Success(result) => {
					sender ! result
					log.info("envelope saved")
				}
				case Failure(e) =>
          log.error(s"storage is down ${e.getMessage}", e)
					sender ! e
			}
	}

	def findEnvelopeById(byId: String, sender: ActorRef) = replyTo(sender, envelopeRepository.get(byId))

	def remove(id: String, sender: ActorRef) = replyTo(sender, envelopeRepository.delete(id))

	def addFile(envelopeId: String, fileId: String, sender: ActorRef) = replyTo(sender, envelopeRepository.addFile(envelopeId, fileId))

	def addFile(metadata: FileMetadata, sender: ActorRef) = replyTo(sender, envelopeRepository.addFile(metadata))

	def getFileMetadata(id: String, sender: ActorRef) = replyTo(sender, envelopeRepository.getFileMetadata(id))

	def replyTo[T](sender: ActorRef, future: Future[T]) = future.onComplete{
		case Success(result) => sender ! result
		case Failure(e)      => sender ! e
	}

	override def postStop(): Unit = {
	  log.info("Envelope storage is going offline")
	  super.postStop()
  }
}
