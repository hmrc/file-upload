package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import org.scalatest.Matchers
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by jay on 11/07/2016.
  */
trait IntegrationSpec extends UnitSpec with OneServerPerSuite with ScalaFutures with IntegrationPatience with Matchers with Status {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString
}
