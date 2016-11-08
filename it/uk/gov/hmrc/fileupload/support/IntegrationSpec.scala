package uk.gov.hmrc.fileupload.support

import java.util.UUID

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.mongo.MongoSpecSupport
import scala.concurrent.ExecutionContext.Implicits._

trait IntegrationSpec extends FeatureSpec with GivenWhenThen with OneServerPerSuite with ScalaFutures
  with IntegrationPatience with Matchers with Status with Eventually with FakeConsumingService
  with BeforeAndAfterEach with MongoSpecSupport{

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  override lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
      "auditing.enabled" -> "false",
      "feature.basicAuthEnabled" -> "true"
    )
  )

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  override def afterAll {
    mongo.apply().drop.futureValue
  }
}
