package uk.gov.hmrc.fileupload.support

import java.io.{File => JFile}

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import play.api.Play.current
import play.api.http.HeaderNames
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileRefId}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.io.Source

trait EnvelopeActions extends ActionsSupport {

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def createEnvelope(file: JFile): WSResponse = createEnvelope(Source.fromFile(file).mkString)

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse =
    WS
      .url(s"$url/envelopes")
      .withHeaders("Content-Type" -> "application/json")
      .post(data)
      .futureValue

  def createEnvelopeWithId(id: String, data: String): WSResponse = createEnvelopeWithId(id, data.getBytes())

  def createEnvelopeWithId(id: String, data: Array[Byte]): WSResponse =
    WS
      .url(s"$url/envelopes/$id")
      .withHeaders("Content-Type" -> "application/json")
      .put(data)
      .futureValue

  def getEnvelopeFor(id: EnvelopeId): WSResponse =
    WS
      .url(s"$url/envelopes/$id").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
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
      .url(s"$url/envelopes/$id").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .delete()
      .futureValue

  def deleteEnvelopWithWrongAuth(id: EnvelopeId): WSResponse =
    WS
      .url(s"$url/envelopes/$id").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yua:yaunspassword")))
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
      .url(s"$fileTransferUrl/envelopes${ destination.map(d => s"?destination=$d").getOrElse("") }").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .get()
      .futureValue
  }

  def getEnvelopesForStatus(status: List[String], inclusive: Boolean) = {
    val statuses = status.map(n => s"status=$n").mkString("&")
    WS
      .url(s"$url/envelopes?$statuses&inclusive=$inclusive").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .getStream()
      .futureValue
  }

  def archiveEnvelopFor(id: EnvelopeId): WSResponse =
    WS
      .url(s"$fileTransferUrl/envelopes/$id")
      .delete()
      .futureValue

}
