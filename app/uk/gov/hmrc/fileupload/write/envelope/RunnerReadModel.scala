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

package uk.gov.hmrc.fileupload.write.envelope

import java.util.UUID

import akka.actor.ActorSystem
import play.api.libs.json.Json
import reactivemongo.api.MongoDriver
import uk.gov.hmrc.fileupload.domain.EventSerializer.{EventReader, EventWriter}
import uk.gov.hmrc.fileupload.domain._
import uk.gov.hmrc.fileupload.read.envelope.{EnvelopeReportActor, Repository}
import uk.gov.hmrc.fileupload.read.infrastructure.CoordinatorActor
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.Await
import scala.concurrent.duration._

object RunnerReadModel extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  //publish/subscribe for write/read communication
  val actorSystem = ActorSystem()
  val eventStream = actorSystem.eventStream
  val subscribe = eventStream.subscribe _
  implicit val publish = eventStream.publish _

  // read model

  //create mongo driver
  val mongoDriver = new MongoDriver
  val connection = mongoDriver.connection(List("localhost"))
  val repository = Repository(() => connection.db("eventsourcing"))

  actorSystem.actorOf(
    CoordinatorActor.props(
      EnvelopeReportActor.props(
        repository.get,
        repository.update,
        defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id)),
      Set(classOf[EnvelopeCreated], classOf[FileQuarantined], classOf[NoVirusDetected]),
      subscribe), "envelopeReportEventHandler")

  // publish events

  Thread.sleep(3000)

  val envelopeId = EnvelopeId("e-4")

  publishEvent(EnvelopeCreated(envelopeId), Version(1))
  publishEvent(FileQuarantined(envelopeId, FileId("file-1"), FileRefId("a"), "test.pdf", "pdf", Json.obj()), Version(2))
  publishEvent(NoVirusDetected(envelopeId, FileId("file-1"), FileRefId("a")), Version(3))

  def publishEvent(envelopeEvent: EnvelopeEvent, version: Version) =
    publish(
      Event(
        EventId(UUID.randomUUID().toString),
        StreamId(envelopeEvent.id.value),
        version,
        Created(System.currentTimeMillis()),
        EventType(envelopeEvent.getClass.getName),
        envelopeEvent))

}
