package uk.gov.hmrc.fileupload.support

import java.io.{File => JFile}

import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.envelope.{EnvelopeStatus, Repository}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.Future
import scala.io.Source

trait EnvelopeActions extends ActionsSupport with MongoSpecSupport {

  def createEnvelope(file: JFile): WSResponse = createEnvelope(Source.fromFile(file).mkString)

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

  // todo (konrad) to be deleted after we have a direct way to change status and destination of an envelope
  val repo = new Repository(mongo) {
    def setStatusAndDestination(envelopeId: String, status: EnvelopeStatus, destination: String): Future[Boolean] = {
      val selector = Json.obj(_Id -> envelopeId)
      val update = Json.obj("$set" -> Json.obj("status" -> status.name, "destination" -> destination))
      collection.update(selector, update).map { _.nModified == 1 }
    }
  }

  def createEnvelopeWithStatusAndDestination(status: EnvelopeStatus, destination: String): EnvelopeId = {
    val id = createEnvelope()
    val resultOfUpdating = repo.setStatusAndDestination(id.value, status, destination).futureValue
    if (resultOfUpdating) {
      id
    } else {
      throw new Exception("failed to update envelope")
    }
  }

  def getEnvelopesForDestination(destination: Option[String]): WSResponse = {
    WS
      .url(s"$fileTransferUrl/non-stub/envelopes${destination.map(d => s"?destination=$d").getOrElse("")}")
      .get()
      .futureValue
  }

}
