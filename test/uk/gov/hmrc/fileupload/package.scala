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

package uk.gov.hmrc

import java.util.UUID

import _root_.play.api.libs.iteratee.{Enumerator, Iteratee}
import _root_.play.api.libs.json.{JsString, JsValue, Json}
import akka.actor.{Actor, ActorRef}
import akka.testkit.TestActorRef
import org.joda.time.DateTime
import reactivemongo.api.DBMetaCommands
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.api.gridfs.{GridFS, ReadFile}
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.envelope.{Constraints, Envelope, Open}
import uk.gov.hmrc.fileupload.file.Repository

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.language.implicitConversions
import scala.util.Try

/**
  * Created by Josiah on 6/4/2016.
  */
package object fileupload {

	type ByteStream = Array[Byte]
	type JSONGridFS = GridFS[JSONSerializationPack.type]
	type JSONReadFile = ReadFile[JSONSerializationPack.type, JsValue]
	type StreamedResults = Future[(JSONReadFile, Try[Boolean])]

  object Support{
    import reactivemongo.api.commands.WriteResult
    import reactivemongo.api.{MongoConnection, FailoverStrategy, DB}

    import scala.concurrent.{Future, ExecutionContext}

    class BlockingExecutionContext extends ExecutionContext{

      override def execute(runnable: Runnable): Unit = Try(runnable.run())

      override def reportFailure(cause: Throwable): Unit = throw cause
    }

    val blockingExeContext: ExecutionContext = new BlockingExecutionContext()

    def consume(data: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Array[Byte] = {
      val futureResult: Future[Array[Byte]] = data  |>>> Iteratee.consume[Array[Byte]]()
      Await.result(futureResult, 500 millis)
    }

	  def constraints = Constraints(contentTypes = Some(Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document")), maxItems = Some(100), maxSize = Some("12GB"), maxSizePerItem = Some("10MB"))
	  def envelope = new Envelope(_id = UUID.randomUUID().toString, constraints = Some(constraints), callbackUrl = Some("http://absolute.callback.url"),
			expiryDate = Some(DateTime.now().plusDays(1)), metadata = Some(Map("anything" -> JsString("the caller wants to add to the envelope"))), status = Open)

	  val envelopeBody = Json.toJson[Envelope](envelope)

	  def expiredEnvelope = envelope.copy(expiryDate = Some(DateTime.now().minusMinutes(3)))
	  def farInTheFutureEnvelope = envelope.copy(expiryDate = Some(DateTime.now().plusDays(3)))

	  object Implicits{
		  implicit def underLyingActor[T <: Actor](actorRef: ActorRef): T = actorRef.asInstanceOf[TestActorRef[T]].underlyingActor
	  }

	  class DBStub extends DB with DBMetaCommands {
		  override def connection: MongoConnection = ???

		  override def failoverStrategy: FailoverStrategy = ???

		  override def name: String = ???
	  }

	  object FileRepositoryStub {
		  def OkWriteResult(n: Int) : Future[WriteResult] = Future.successful(DefaultWriteResult(ok = true, n, Seq(), None, Some(1), None))
	  }

	  class FileRepositoryStub(var data: Map[String, Envelope] = Map(), val iteratee: Iteratee[ByteStream, Future[JSONReadFile]]) extends Repository(() => new DBStub){

//		  override def add(envelope: Envelope)(implicit ex: ExecutionContext): Future[Boolean] = {
//			  data = data ++ Map(envelope._id -> envelope)
//			  Future.successful(true)
//		  }
//
//		  override def get(byId: String)(implicit ec: ExecutionContext): Future[Option[Envelope]] = Future.successful(data.get(byId))

		  override def iterateeForUpload(envelopeId: String, file: String)(implicit ec: ExecutionContext): Iteratee[ByteStream, Future[JSONReadFile]] = iteratee
	  }
  }
}
