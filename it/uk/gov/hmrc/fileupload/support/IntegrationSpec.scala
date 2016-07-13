package uk.gov.hmrc.fileupload.support

import java.util.UUID

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status

trait IntegrationSpec extends FeatureSpec with GivenWhenThen with OneServerPerSuite with ScalaFutures with IntegrationPatience with Matchers with Status {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString
}
