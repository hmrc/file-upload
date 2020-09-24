package uk.gov.hmrc.fileupload.support

import org.scalatest.TestSuite
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status
import uk.gov.hmrc.fileupload.IntegrationTestApplicationComponents

trait ActionsSupport extends ScalaFutures with Status with IntegrationTestApplicationComponents with IntegrationPatience {
  this: TestSuite =>

  lazy val url = s"http://localhost:$port/file-upload"
  lazy val fileTransferUrl = s"http://localhost:$port/file-transfer"
  lazy val fileRoutingUrl = s"http://localhost:$port/file-routing"
  val client = new play.api.test.WsTestClient.InternalWSClient(scheme = "http", port = -1)
}
