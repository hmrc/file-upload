package uk.gov.hmrc.fileupload.support

import java.io.File

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.EnvelopeId

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

  def getEnvelopeFor(id: EnvelopeId): WSResponse =
    WS
      .url(s"$url/envelope/$id")
      .get()
      .futureValue

  def envelopeIdFromHeader(response: WSResponse): EnvelopeId = {
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def createEnvelope(): EnvelopeId = {
    val response: WSResponse = createEnvelope( EnvelopeReportSupport.requestBody() )
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def deleteEnvelopFor(id: EnvelopeId): WSResponse =
    WS
      .url(s"$url/envelope/$id")
      .delete()
      .futureValue

}
