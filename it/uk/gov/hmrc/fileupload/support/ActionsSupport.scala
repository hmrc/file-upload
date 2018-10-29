package uk.gov.hmrc.fileupload.support

import org.scalatest.TestSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status
import uk.gov.hmrc.fileupload.IntegrationTestApplicationComponents

trait ActionsSupport extends ScalaFutures with Status with IntegrationTestApplicationComponents{
  this: TestSuite =>

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val url = "http://localhost:9000/file-upload"
  val fileTransferUrl = "http://localhost:9000/file-transfer"
  val fileRoutingUrl = "http://localhost:9000/file-routing"
  val client = components.wsClient
}
