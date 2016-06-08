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

import _root_.play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator}
import _root_.play.api.libs.json.{JsString, Json}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, DefaultTimeout, TestKit}
import org.joda.time.DateTime
import org.openqa.selenium.By.ById
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import reactivemongo.api.commands.{DefaultWriteResult, WriteResult}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.models.Constraints

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

/**
  * Created by Josiah on 6/4/2016.
  */
package object fileupload {

  object Support{
    import reactivemongo.api.commands.WriteResult
    import reactivemongo.api.{MongoConnection, FailoverStrategy, DB}
    import uk.gov.hmrc.fileupload.models.Envelope
    import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

    import scala.concurrent.{Future, ExecutionContext}

    class BlockingExecutionContext extends ExecutionContext{

      override def execute(runnable: Runnable): Unit = runnable.run()

      override def reportFailure(cause: Throwable): Unit = throw cause
    }

    val blockingExeContext: ExecutionContext = new BlockingExecutionContext()

    def consume(data: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Array[Byte] = {
      val futureResult: Future[Array[Byte]] = data  |>>> Iteratee.consume[Array[Byte]]()
      Await.result(futureResult, 500 millis)
    }

	  def constraints = Constraints(contentTypes = Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), maxItems = 100, maxSize = "12GB", maxSizePerItem = "10MB")
	  def envelope = new Envelope(_id = BSONObjectID.generate, constraints = constraints, callbackUrl = "http://absolute.callback.url", expiryDate = DateTime.now().plusDays(1), metadata = Map("anything" -> JsString("the caller wants to add to the envelope")))


	  val envelopeBody = Json.toJson[Envelope](envelope )

	  def expiredEnvelope = envelope.copy(expiryDate = DateTime.now().minusMinutes(3))
	  def farInTheFutureEnvelope = envelope.copy(expiryDate = DateTime.now().plusDays(3))

  }
}
