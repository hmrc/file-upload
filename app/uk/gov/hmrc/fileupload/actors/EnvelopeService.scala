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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.fileupload.actors.EnvelopeService.{CreateEnvelope, DeleteEnvelope, GetEnvelope, UpdateEnvelope}
import uk.gov.hmrc.fileupload.actors.IdGenerator.NextId
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeNotFoundException}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.pattern._
import uk.gov.hmrc.fileupload.actors.Marshaller.UnMarshall

import scala.language.postfixOps
import scala.util.{Failure, Success}

object EnvelopeService {

  case class GetEnvelope(id: String)

  case class CreateEnvelope(json: Option[JsValue])

  case class DeleteEnvelope(id: String)

  case class UpdateEnvelope(envelopeId: String, fileId: String)

  case object EnvelopeUpdated

  case object EnvelopeNotFound

  def props(storage: ActorRef, idGenerator: ActorRef, marshaller: ActorRef, maxTTL: Int): Props =
    Props(classOf[EnvelopeService], storage, idGenerator, marshaller, maxTTL)
}

class EnvelopeService(storage: ActorRef, idGenerator: ActorRef, marshaller: ActorRef, maxTTL: Int) extends Actor with ActorLogging {

  import Storage._
  import Marshaller._
  import uk.gov.hmrc.fileupload.actors.Implicits.FutureUtil

  implicit val ex: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(2 seconds)

  override def preStart() {
    log.info("Envelope manager online")
    super.preStart()
  }


  def receive = {
    case GetEnvelope(id) => getEnvelopeFor(id, sender)
    case CreateEnvelope(data) => createEnvelopeFrom(data, sender)
    case DeleteEnvelope(id) => storage forward Remove(id)
    case UpdateEnvelope(envelopeId, fileId) =>
      println(s"forwarding add file message from $sender to storage")
      // FIXME we can't just forward this request to the storage
      // FIXME we have to wait on the storage and then rollback the fileupload
      // FIXME on failure
      storage forward AddFile(envelopeId, fileId)
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

  def createEnvelopeFrom(data: Option[JsValue], sender: ActorRef): Unit = {
    log.info(s"processing CreateEnvelope")
    (idGenerator ? NextId)
      .mapTo[String]
      .map(id => {
        val d = data.getOrElse(Json.toJson( Envelope.emptyEnvelope() ))
        d.asInstanceOf[JsObject] ++ Json.obj("_id" -> id)
      })
      .flatMap(marshaller ? UnMarshall(_, classOf[Envelope])) // move this to the controller
      .breakOnFailure
      .mapTo[Envelope]
      .map(e => {
        var newMaxItems: Int = 1
        e.constraints.foreach(c => c.maxItems.foreach(newMaxItems = _))
        Storage.Save(e.copy(constraints = e.constraints.map(newConstraint => newConstraint.copy(maxItems = Some(newMaxItems)))))
      })
      .onComplete {
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
