package uk.gov.hmrc.fileupload.controllers

import akka.util.Timeout
import play.api.http.Status
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.concurrent.ExecutionContext


/**
  * Created by jay on 11/07/2016.
  */
trait ITestSupport extends FutureAwaits with DefaultAwaitTimeout with Status {
  import scala.concurrent.duration._

  val url = s"http://localhost:9000/file-upload"
  implicit val ec = ExecutionContext.global
  implicit val timeout = Timeout(1 minute)

}
