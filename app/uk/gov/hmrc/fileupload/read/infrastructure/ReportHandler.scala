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

import uk.gov.hmrc.fileupload.write.infrastructure._

import scala.concurrent.Future

trait Report[T, Id] {

  def toId: StreamId => Id
  def save: T => Future[Option[T]]
  def delete: Id => Future[Boolean]
  def defaultState: Id => T
  def updateVersion: (Version, T) => T

  var eventVersion = Report.defaultVersion
  var created = Report.defaultCreated

  def handle(events: Seq[Event]): Unit = {
    events.headOption.foreach { event =>
      val id = toId(event.streamId)
      var currentState = Option(defaultState(id))

      events.foreach { e =>
        eventVersion = e.version
        created = e.created
        currentState = currentState.flatMap { s =>
          apply(s -> e.eventData)
        }
      }

      currentState match {
        case Some(entity) =>
          save(updateVersion(eventVersion, entity))
        case None =>
          delete(id)
      }
    }
  }

  def apply: PartialFunction[(T, EventData), Option[T]]
}

object Report {

  val defaultVersion = Version(0)
  val defaultCreated = Created(0)
}
