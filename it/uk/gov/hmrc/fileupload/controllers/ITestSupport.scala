package uk.gov.hmrc.fileupload.controllers

import java.io.File

import akka.util.Timeout
import play.api.http.Status
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.{FutureAwaits, DefaultAwaitTimeout}
import play.test.WithServer

import scala.concurrent.{Future, ExecutionContext}
import scala.io.Source

/**
  * Created by jay on 11/07/2016.
  */
trait ITestSupport extends FutureAwaits with DefaultAwaitTimeout with Status {
  import scala.concurrent.duration._

  val url = "http://localhost:9000/file-upload"
  implicit val ec = ExecutionContext.global
  implicit val timeout = Timeout(1 minute)

}
