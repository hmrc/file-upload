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

import akka.actor.{Actor, PoisonPill, ReceiveTimeout}
import uk.gov.hmrc.fileupload.utils.Contexts
import uk.gov.hmrc.fileupload.write.infrastructure.{Created, Event, EventData, Version}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait ReportActor[T, Id] extends Actor {

  def id: Id
  def get: Id => Future[Option[T]]
  def save: T => Future[Boolean]
  def delete: Id => Future[Boolean]
  def defaultState: Id => T

  def awaitTimeout: Duration = 5.seconds
  def receiveTimeout: Duration = 1.seconds

  var currentState: T = defaultState(id)
  var eventVersion: Version = Version(0)
  var created: Created = Created(0)

  override def preStart = {
    currentState = Await.result(get(id).map(_.getOrElse(currentState))(Contexts.blockingDb), awaitTimeout)
    context.setReceiveTimeout(receiveTimeout)
  }

  // todo verify correct version was used once we have a unit of work instead of single events
  def receive = {
    case Event(eventId, streamId, v: Version, eventDate, eventType, eventData: EventData) =>
      eventVersion = v
      created = eventDate
      apply(currentState -> eventData) match {
        case Some(entity) =>
          currentState = entity
          Await.result(save(currentState), awaitTimeout)
        case None =>
          currentState = defaultState(id)
          Await.result(delete(id), awaitTimeout)
      }
    case ReceiveTimeout => self ! PoisonPill // current state will become stale sooner or later
  }

  def apply: PartialFunction[(T, EventData), Option[T]]

}
