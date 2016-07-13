package uk.gov.hmrc.fileupload.controllers

import java.io.File


import play.api.http.Status
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.test.WithServer
import scala.concurrent.duration._
import play.api.Play.current
import scala.concurrent.Future
import scala.io.Source

/**
  * Created by jay on 11/07/2016.
  */
trait EnvelopeActions extends ITestSupport {

  def createEnvelope(file: File): WSResponse = createEnvelope(Source.fromFile(file).mkString)

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse = {
    await {
      WS
        .url(s"$url/envelope")
        .withHeaders("Content-Type" -> "application/json")
        .post(data)
    }
  }

  def getEnvelopeFor(id: String): WSResponse = {
    await {
      WS
        .url(s"$url/envelope/$id")
        .get()
    }
  }
}
