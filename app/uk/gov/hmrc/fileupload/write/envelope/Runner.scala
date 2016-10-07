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
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import cats.data.Xor
import play.api.libs.json.Json
import reactivemongo.api.MongoDriver
import uk.gov.hmrc.fileupload.read.envelope.{EnvelopeReportHandler, Repository}
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object Runner extends App {

//  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

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

  val envelopeReport = new EnvelopeReportHandler(
    (streamId: StreamId) => EnvelopeId(streamId.value),
    repository.update,
    repository.delete,
    defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id))

  // write model

  //this we can create inside microserviceGlobal
//  implicit val eventStore = new InMemoryEventStore()

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  implicit val eventStore = new MongoEventStore(() => connection.db("eventsourcing"))

  val handle = (command: EnvelopeCommand) => new Aggregate(
    Envelope,
    () => Envelope(),
    (a: AnyRef) => {
//      println(s"handle $a")
    },
    envelopeReport.handle)
    .handleCommand(command)

  val serviceWhichCallsCommandFunc = serviceWhichCallsCommand(handle) _
  
  val envelopeId = EnvelopeId(UUID.randomUUID().toString)

  serviceWhichCallsCommandFunc(new CreateEnvelope(envelopeId, Some("http://test.com"), None, None))
  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-1"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new MarkFileAsClean(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-1")))

  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-1"), FileRefId("file-reference-id-11"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new QuarantineFile(envelopeId, FileId("file-id-2"), FileRefId("file-reference-id-21"), 0, "example.pdf", "application/pdf", Json.obj("name" -> "test")))
  serviceWhichCallsCommandFunc(new MarkFileAsClean(envelopeId, FileId("file-id-2"), FileRefId("file-reference-id-21")))

  //print read model
  Thread.sleep(3000)
  val r = Await.result(repository.get(envelopeId), 5 seconds)
  println(r)
  println(r.map(_.version == Version(6)))

//  System.exit(-1)

  def serviceWhichCallsCommand(handle: (EnvelopeCommand) => Future[Xor[CommandNotAccepted, CommandAccepted.type]])(command: EnvelopeCommand) = {
    val result = Await.result(handle(command), 5 seconds)
//    handle(command).onComplete {
//      case Success(v) => println(v)
//      case Failure(f) => println(f)
//    }
    println(result)
  }
}
