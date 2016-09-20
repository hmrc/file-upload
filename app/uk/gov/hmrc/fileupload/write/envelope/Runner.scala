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
import cats.data.Xor
import play.api.libs.json.Json
import reactivemongo.api.MongoDriver
import uk.gov.hmrc.fileupload.read.envelope.{EnvelopeReportActor, Repository}
import uk.gov.hmrc.fileupload.read.infrastructure.CoordinatorActor
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, CommandAccepted, MongoEventStore}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{Await, Future}
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

  actorSystem.actorOf(
    CoordinatorActor.props(
      EnvelopeReportActor.props(
        repository.get,
        repository.update,
        repository.delete,
        defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id)),
      Set(classOf[EnvelopeCreated], classOf[FileQuarantined], classOf[NoVirusDetected]),
      subscribe), "envelopeReportEventHandler")

  // write model

  //this we can create inside microserviceGlobal
//  implicit val eventStore = new InMemoryEventStore()

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  implicit val eventStore = new MongoEventStore(() => connection.db("eventsourcing"))

  val handle = (command: EnvelopeCommand) => Aggregate(
    Envelope, () => Envelope(), (msg) => EnvelopeCommandError(msg), publish)
    .handleCommand(command)

  val serviceWhichCallsCommandFunc = serviceWhichCallsCommand(handle) _
  
  val envelopeId = EnvelopeId(UUID.randomUUID().toString)

  serviceWhichCallsCommandFunc(new CreateEnvelope(envelopeId, Some("http://test.com")))
  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-1"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new MarkFileAsClean(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-1")))

  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-2"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-2"), FileRefId("file-reference-id-21"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new MarkFileAsClean(envelopeId, FileId("file-id-2"), FileRefId("file-reference-id-21")))

  //print read model
  Thread.sleep(3000)
  println(Await.result(repository.get(envelopeId), 5 seconds))

  def serviceWhichCallsCommand(handle: (EnvelopeCommand) => Future[Xor[EnvelopeCommandNotAccepted, CommandAccepted.type]])(command: EnvelopeCommand) = {
    val result = Await.result(handle(command), 5 seconds)
    println(result)
  }
}
