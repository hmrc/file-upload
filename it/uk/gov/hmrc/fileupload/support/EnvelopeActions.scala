package uk.gov.hmrc.fileupload.support

import java.io.{File => JFile}

import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.io.Source

trait EnvelopeActions extends ActionsSupport with MongoSpecSupport {

  def createEnvelope(file: JFile): WSResponse = createEnvelope(Source.fromFile(file).mkString)

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse =
    WS
      .url(s"$url/envelopes")
      .withHeaders("Content-Type" -> "application/json")
      .post(data)
      .futureValue

  def getEnvelopeFor(id: EnvelopeId): WSResponse =
    WS
      .url(s"$url/envelopes/$id")
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
      .url(s"$url/envelopes/$id")
      .delete()
      .futureValue

  def submitRoutingRequest(envelopeId: EnvelopeId, destination: String, application: String = "testApplication"): WSResponse = {
    val payload = Json.obj(
      "envelopeId" -> envelopeId,
      "destination" -> destination,
      "application" -> application
    )
    WS.url(s"$fileRoutingUrl/requests")
      .post(payload)
      .futureValue
  }

  def getEnvelopesForDestination(destination: Option[String]): WSResponse = {
    WS
      .url(s"$fileTransferUrl/envelopes${ destination.map(d => s"?destination=$d").getOrElse("") }")
      .get()
      .futureValue
  }

}
