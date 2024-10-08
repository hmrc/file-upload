/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.actor.{Actor, ActorRef, Props}
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.read.notifier.NotifierRepository.{Notification, NotifyResult}
import uk.gov.hmrc.fileupload.write.envelope.{FileQuarantined, FileStored, NoVirusDetected, VirusDetected}
import uk.gov.hmrc.fileupload.write.infrastructure.{Event, EventData}

import scala.concurrent.{ExecutionContext, Future}

class NotifierActor(
  subscribe   : (ActorRef, Class[_])   => Boolean,
  findEnvelope: EnvelopeId             => Future[FindResult],
  notify      : (Notification, String) => Future[NotifyResult]
)(using
  ExecutionContext
) extends Actor:

  private val logger = Logger(getClass)

  override def preStart(): Unit =
    subscribe(self, classOf[Event])

  def receive = {
    case event: Event => event.eventData match
      case e: FileQuarantined =>
        logger.info(s"Quarantined event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "QUARANTINED", None))
      case e: NoVirusDetected =>
        logger.info(s"NoVirusDetected event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "CLEANED", None))
      case e: VirusDetected =>
        logger.info(s"VirusDetected event received for ${e.id} and ${e.fileId} ")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "ERROR", Some("VirusDetected")))
      case e: FileStored =>
        logger.info(s"FileStored event received for ${e.id} and ${e.fileId}")
        notifyEnvelopeCallback(Notification(e.id, e.fileId, "AVAILABLE", None))
      case e: EventData =>
        logger.debug(s"Not notifying for ${e.getClass.getName}")
  }

  def notifyEnvelopeCallback(notification: Notification) = {
    findEnvelope(notification.envelopeId).flatMap:
      case Right(envelope) =>
        envelope.callbackUrl
          .map: callbackUrl =>
            notify(notification, callbackUrl).map:
              case Right(envelopeId) => logger.info(s"Successfully sent notification [${notification.status}] for envelope [$envelopeId]")
              case Left(error)       => logger.warn(
                                          s"Failed to send notification [${notification.status}] for envelope [${error.envelopeId}]. Reason [${error.reason}]"
                                        )
          .getOrElse(Future.successful(()))
      case Left(e) =>
        logger.warn(e.toString)
        Future.successful(())
  }
end NotifierActor

object NotifierActor:
  def props(
    subscribe   : (ActorRef, Class[_])   => Boolean,
    findEnvelope: EnvelopeId             => Future[FindResult],
    notify      : (Notification, String) => Future[NotifyResult]
  )(using
    ExecutionContext
  ) =
    Props(NotifierActor(
      subscribe    = subscribe,
      findEnvelope = findEnvelope,
      notify       = notify
    ))
