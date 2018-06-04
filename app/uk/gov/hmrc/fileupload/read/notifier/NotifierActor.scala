/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.notifier

import akka.actor.{Actor, ActorRef, Props}
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.read.notifier.NotifierRepository.{Notification, NotifyResult}
import uk.gov.hmrc.fileupload.write.envelope.{FileQuarantined, FileStored, NoVirusDetected, VirusDetected}
import uk.gov.hmrc.fileupload.write.infrastructure.{Event, EventData}

import scala.concurrent.{ExecutionContext, Future}

class NotifierActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    findEnvelope: (EnvelopeId) => Future[FindResult],
                    notify: (Notification, String) => Future[NotifyResult])
                   (implicit executionContext: ExecutionContext) extends Actor {

  override def preStart = subscribe(self, classOf[Event])

  def receive = {
    case event: Event => event.eventData match {
      case e: FileQuarantined =>
        Logger.info(s"Quarantined event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "QUARANTINED", None))
      case e: NoVirusDetected =>
        Logger.info(s"NoVirusDetected event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "CLEANED", None))
      case e: VirusDetected =>
        Logger.info(s"VirusDetected event received for ${e.id} and ${e.fileId} ")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "ERROR", Some("VirusDetected")))
      case e: FileStored =>
        Logger.info(s"FileStored event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "AVAILABLE", None))
      case e: EventData =>
        Logger.debug(s"Not notifying for ${e.getClass.getName}")
    }
  }

  def notifyEnvelopeCallback(notification: Notification) =
    findEnvelope(notification.envelopeId).map {
      case Xor.Right(envelope) => envelope.callbackUrl.foreach(notify(notification, _))
      case Xor.Left(e) => Logger.warn(e.toString)
    }
}

object NotifierActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            findEnvelope: (EnvelopeId) => Future[FindResult],
            notify: (Notification, String) => Future[NotifyResult])
           (implicit executionContext: ExecutionContext) =
    Props(new NotifierActor(subscribe = subscribe, findEnvelope = findEnvelope, notify = notify))
}
