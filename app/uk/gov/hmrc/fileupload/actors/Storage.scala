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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Try, Failure, Success}

object Storage{
  case class FindById(id: BSONObjectID)
	case class Save(envelope: Envelope)
	case class Remove(id: BSONObjectID)

  def props(envelopeRepository: EnvelopeRepository): Props = Props(classOf[Storage], envelopeRepository)

}

class Storage(val envelopeRepository: EnvelopeRepository) extends Actor with ActorLogging{
  import Storage._

  implicit val ex: ExecutionContext = context.dispatcher

	override def preStart(): Unit = {
    log.info("Envelope storage online")
		super.preStart()
  }

  def receive = {
    case FindById(id) => findEnvelopeById(id, sender)
    case Save(envelope) => save(envelope, sender)
    case Remove(id) => remove(id, sender)
  }

  def findEnvelopeById(byId: BSONObjectID, sender: ActorRef): Unit = {
    envelopeRepository
	    .get(byId)
	    .onComplete{
	      case Success(result)   => sender ! result
	      case Failure(t)       => sender ! t
      }
  }

	def save(envelope: Envelope, sender: ActorRef): Unit = {
		envelopeRepository
			.add(envelope)
			.onComplete {
				case Success(result) => sender ! envelope._id
				case Failure(e) =>
					println(s"storage is down ${e.getMessage}")
					sender ! e
			}
	}

	def remove(id: BSONObjectID, sender: ActorRef): Unit = {
		envelopeRepository.delete(id).onComplete{
			case Success(result) => sender ! result
			case Failure(e) => sender ! e
		}
	}

  override def postStop(): Unit = {
	  log.info("Envelope storage is going offline")
	  super.postStop()
  }
}
