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

package uk.gov.hmrc.fileupload.stats

import akka.actor.{Actor, ActorRef, Props}
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.read.notifier.NotifierRepository.{Notification, NotifyResult}
import uk.gov.hmrc.fileupload.write.envelope.{FileQuarantined, FileStored, NoVirusDetected, VirusDetected}
import uk.gov.hmrc.fileupload.write.infrastructure.{Event, EventData}

import scala.concurrent.{ExecutionContext, Future}

class StatsActor(subscribe: (ActorRef, Class[_]) => Boolean,
                 notify: (Notification, String) => Future[NotifyResult])
                (implicit executionContext: ExecutionContext) extends Actor {

  override def preStart = {
    subscribe(self, classOf[Event])
  }

  def receive = {
    case event: Event => event.eventData match {
      case e: FileQuarantined =>
        Stats.save(e)
      case e: VirusDetected =>
        Stats.delete(e)
      case e: FileStored =>
        Stats.delete(e)
    }
  }
}

object StatsActor {
  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            findEnvelope: (EnvelopeId) => Future[FindResult],
            notify: (Notification, String) => Future[NotifyResult])
           (implicit executionContext: ExecutionContext) =
    Props(new StatsActor(subscribe = subscribe, notify = notify))
}
