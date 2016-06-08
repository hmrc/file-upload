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

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import akka.util.Timeout
import play.api.libs.json.JsValue
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.actors.EnvelopeService.{DeleteEnvelope, CreateEnvelope, GetEnvelope}
import uk.gov.hmrc.fileupload.actors.IdGenerator.NextId
import uk.gov.hmrc.fileupload.models.Envelope
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object EnvelopeService{
  case class GetEnvelope(id: String)
	case class CreateEnvelope(json: JsValue)
	case class DeleteEnvelope(id: String)

  def props(storage: ActorRef, idGenerator: ActorRef, maxTTL: Int): Props = Props(classOf[EnvelopeService], storage, idGenerator, maxTTL)
}

class EnvelopeService(storage: ActorRef, idGenerator: ActorRef, maxTTL: Int) extends Actor with ActorLogging{
	import Storage._
  implicit val ex: ExecutionContext = context.dispatcher
	implicit val timeout = Timeout(500 millis)

  override def preStart() {
    log.info("Envelope manager online")
	  super.preStart()
  }

  def receive = {
    case GetEnvelope(id) => storage forward FindById(BSONObjectID(id))
    case CreateEnvelope(data) => createEnvelopeFrom(data, sender())
    case DeleteEnvelope(id) => storage forward Remove(BSONObjectID(id))
  }

	def createEnvelopeFrom(data: JsValue, sender: ActorRef): Unit = {
		log.info(s"processing CreateEnvelope")
		(idGenerator ? NextId)
		  .mapTo[BSONObjectID]
			.map(Envelope.fromJson(data, _, maxTTL))
			.map(Storage.Save)
			.onComplete{
			  case Success(msg) => storage.!(msg)(sender)
			  case Failure(e) =>
				  log.error(s"$e during envelope creation")
				  sender ! e
			}

	}

  override def postStop() {
	  log.info("Envelope storage is going offline")
	  super.postStop()
  }

}
