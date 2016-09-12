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
import uk.gov.hmrc.fileupload.domain.MongoEventStore
import uk.gov.hmrc.fileupload.read.envelope.{EventHandler, Repository}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}

import scala.concurrent.Await
import scala.concurrent.duration._

object Runner extends App {

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

  actorSystem.actorOf(EventHandler.props(subscribe, repository), "envelopeReportEventHandler")

  // write model

  //this we can create inside microserviceGlobal
//  implicit val eventStore = new InMemoryEventStore()

  implicit val reader = new EventReader(EventSerializer.toEventData)
  implicit val writer = new EventWriter(EventSerializer.fromEventData)

  implicit val eventStore = new MongoEventStore(() => connection.db("eventsourcing"))
  val handle = CommandHandler.handleCommand _

  val serviceWhichCallsCommandFunc = serviceWhichCallsCommand(handle) _
  
  val envelopeId = EnvelopeId(UUID.randomUUID().toString)

  serviceWhichCallsCommandFunc(new CreateEnvelope(envelopeId))
  serviceWhichCallsCommandFunc(new QurantineFile(envelopeId, FileId("file-id-1"), FileReferenceId("file-reference-id-1"), "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  Thread.sleep(1000)
  serviceWhichCallsCommandFunc(new MarkFileAsClean(envelopeId, FileId("file-id-1"), FileReferenceId("file-reference-id-1")))

  //print read model
  Thread.sleep(3000)
  println(Await.result(repository.get(envelopeId), 5 seconds))

  def serviceWhichCallsCommand(handle: (EnvelopeCommand) => Unit)(command: EnvelopeCommand) =
    handle(command)
}
