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

package uk.gov.hmrc.fileupload

import akka.actor.{Actor, ActorRef}
import akka.testkit.TestActorRef
import org.joda.time.DateTime
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.{JsString, Json}
import reactivemongo.api.DBMetaCommands
import reactivemongo.api.commands.DefaultWriteResult
import uk.gov.hmrc.fileupload.controllers.EnvelopeReport
import uk.gov.hmrc.fileupload.read.envelope._
import uk.gov.hmrc.fileupload.read.file.Repository

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object Support {

  import reactivemongo.api.commands.WriteResult
  import reactivemongo.api.{DB, FailoverStrategy, MongoConnection}

  import scala.concurrent.{ExecutionContext, Future}

  class BlockingExecutionContext extends ExecutionContext {

    override def execute(runnable: Runnable): Unit = Try(runnable.run())

    override def reportFailure(cause: Throwable): Unit = throw cause
  }

  val blockingExeContext: ExecutionContext = new BlockingExecutionContext()

  def consume(data: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Array[Byte] = {
    val futureResult: Future[Array[Byte]] = data |>>> Iteratee.consume[Array[Byte]]()
    Await.result(futureResult, 500.millis)
  }

  def constraints = Constraints(contentTypes = Some(Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    maxItems = Some(100), maxSize = Some("12GB"), maxSizePerItem = Some("10MB"))

  def envelope = new Envelope(_id = EnvelopeId(), constraints = Some(constraints), callbackUrl = Some("http://absolute.callback.url"),
    expiryDate = Some(DateTime.now().plusDays(1).withMillisOfSecond(0)),
    metadata = Some(Map("anything" -> JsString("the caller wants to add to the envelope"))),
    destination = Some("destination"),
    application = Some("application")
  )

  def envelopeWithAFile(fileId: FileId) = envelope.copy(files = Some(List(File(fileId, fileRefId = FileRefId("ref"), status = FileStatusQuarantined))))

  val envelopeBody = Json.toJson[Envelope](envelope)

  def envelopeReport = EnvelopeReport(callbackUrl = Some("http://absolute.callback.url"))

  val envelopeReportBody = Json.toJson(envelopeReport)

  def expiredEnvelope = envelope.copy(expiryDate = Some(DateTime.now().minusMinutes(3)))

  def farInTheFutureEnvelope = envelope.copy(expiryDate = Some(DateTime.now().plusDays(3)))

  def envelopeAvailable(e: Envelope = envelope): WithValidEnvelope = new WithValidEnvelope(
    _ => Future.successful(Some(e))
  )

  val envelopeNotFound: WithValidEnvelope = new WithValidEnvelope(
    _ => Future.successful(None)
  )

  object Implicits {
    implicit def underLyingActor[T <: Actor](actorRef: ActorRef): T = actorRef.asInstanceOf[TestActorRef[T]].underlyingActor
  }

  class DBStub extends DB with DBMetaCommands {
    override def connection: MongoConnection = ???

    override def failoverStrategy: FailoverStrategy = ???

    override def name: String = ???
  }

  object FileRepositoryStub {
    def OkWriteResult(n: Int): Future[WriteResult] = Future.successful(DefaultWriteResult(ok = true, n, Seq(), None, Some(1), None))
  }

  class FileRepositoryStub(var data: Map[String, Envelope] = Map(), val iteratee: Iteratee[ByteStream, Future[JSONReadFile]]) extends Repository(() => new DBStub) {
    override def iterateeForUpload(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId)(implicit ec: ExecutionContext): Iteratee[ByteStream, Future[JSONReadFile]] = iteratee
  }
}
