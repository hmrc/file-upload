package uk.gov.hmrc.fileupload.support

import java.io.File

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}

import scala.io.Source

trait EnvelopeActions extends ActionsSupport {

  def createEnvelope(file: File): WSResponse = createEnvelope(Source.fromFile(file).mkString)

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse =
    WS
      .url(s"$url/envelope")
      .withHeaders("Content-Type" -> "application/json")
      .post(data)
      .futureValue

  def getEnvelopeFor(id: String): WSResponse =
    WS
      .url(s"$url/envelope/$id")
      .get()
      .futureValue

  def envelopeIdFromHeader(response: WSResponse): String = {
    val locationHeader = response.header("Location").get
    locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
  }

  def createEnvelope(): String = {
    val response: WSResponse = createEnvelope( EnvelopeReportSupport.requestBody() )
    val locationHeader = response.header("Location").get
    locationHeader.substring(locationHeader.lastIndexOf('/') + 1)
  }

  def deleteEnvelopFor(id: String): WSResponse =
    WS
      .url(s"$url/envelope/$id")
      .delete()
      .futureValue

  def seal(id: String): WSResponse = {
      WS
        .url(s"$url/envelope/$id")
        .withHeaders("Content-Type" -> "application/json")
        .put("""{"status": "sealed"}""")
        .futureValue
  }
}
