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

package uk.gov.hmrc.fileupload.read.infrastructure

import akka.actor.{Actor, ActorRef, Props}
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.write.infrastructure.Event

class CoordinatorActor(childProps: (EnvelopeId) => Props, subscribedEventTypes: Set[Class[_]], subscribe: (ActorRef, Class[_]) => Boolean) extends Actor {

  override def preStart = {
    subscribe(self, classOf[Event])
  }

  override def receive = {
    case e: Event if isOneOfSubscribedEventTypes(e) =>
      val child = context.child(e.streamId.value).getOrElse(context.actorOf(childProps(EnvelopeId(e.streamId.value)), e.streamId.value))
      child ! e

    case unhandledEvent: Event => Logger.debug(s"not subscribedTo ${unhandledEvent.eventData.getClass}")
  }

  private def isOneOfSubscribedEventTypes(e: Event) =
    subscribedEventTypes.exists(_.isAssignableFrom(e.eventData.getClass))
}

object CoordinatorActor {

  def props(childProps: (EnvelopeId) => Props, subscribedEventTypes: Set[Class[_]], subscribe: (ActorRef, Class[_]) => Boolean): Props =
    Props(new CoordinatorActor(childProps, subscribedEventTypes, subscribe))
}
