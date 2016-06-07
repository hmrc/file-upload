package uk.gov.hmrc

import _root_.play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator}
import _root_.play.api.libs.json.Json
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, DefaultTimeout, TestKit}
import org.joda.time.DateTime
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import reactivemongo.api.commands.{DefaultWriteResult, WriteResult}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.models.Constraints

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

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

    class DBStub extends DB {
      override def connection: MongoConnection = ???

      override def failoverStrategy: FailoverStrategy = ???

      override def name: String = ???
    }

    object EnvelopRepositoryStub{
      def OkWriteResult(n: Int) : Future[WriteResult] = Future.successful(DefaultWriteResult(ok = true, n, Seq(), None, Some(1), None))
    }

    class EnvelopRepositoryStub(var  data: Map[BSONObjectID, Envelope] = Map()) extends EnvelopeRepository(() => new DBStub){
      import EnvelopRepositoryStub._

      override def persist(envelope: Envelope)(implicit ex: ExecutionContext): Future[WriteResult] = {
        data = data ++ Map(envelope._id -> envelope)
        OkWriteResult(1)
      }

      override def get(byId: BSONObjectID)(implicit ec: ExecutionContext): Future[Option[Envelope]] = Future.successful(data.get(byId))
    }

    val blockingExeContext: ExecutionContext = new BlockingExecutionContext()
    val envelopRepositoryStub = new EnvelopRepositoryStub

    def consume(data: Enumerator[Array[Byte]])(implicit ec: ExecutionContext): Array[Byte] = {
      val futureResult: Future[Array[Byte]] = data  |>>> Iteratee.consume[Array[Byte]]()
      Await.result(futureResult, 500 millis)
    }

	  def constraints = Constraints(contentTypes = Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), maxItems = 100, maxSize = "12GB", maxSizePerItem = "10MB")
	  def envelope = new Envelope(_id = BSONObjectID.generate, constraints = constraints, callbackUrl = "http://absolute.callback.url", expiryDate = DateTime.now().plusDays(1), metadata = Map("anything" -> "the caller wants to add to the envelope"))


	  val envelopeBody = Json.toJson[Envelope](envelope )

	  def expiredEnvelope = envelope.copy(expiryDate = DateTime.now().minusMinutes(3))
	  def farInTheFutureEnvelope = envelope.copy(expiryDate = DateTime.now().plusDays(3))

  }
}
