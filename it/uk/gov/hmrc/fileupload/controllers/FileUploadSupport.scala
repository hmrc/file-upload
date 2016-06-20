package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.json.{JsString, Json}
import play.api.test.WithServer
import uk.gov.hmrc.fileupload.models.{Constraints, Envelope}
import uk.gov.hmrc.fileupload.repositories.{DefaultMongoConnection, EnvelopeRepository}

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws._

/**
  * Created by Josiah on 6/20/2016.
  */
class FileUploadSupport(var mayBeEnvelope: Option[Envelope] = None) extends WithServer{
  import Envelope._

  implicit val ec = ExecutionContext.global

  var self = this
  val url = "http://localhost:9000/file-upload/envelope"
  val repository = new EnvelopeRepository(DefaultMongoConnection.db)

  private val payload = Json.stringify(Json.toJson[Envelope](new Envelope(_id = UUID.randomUUID().toString,
    constraints = Constraints(contentTypes = Seq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), maxItems = 100, maxSize = "12GB", maxSizePerItem = "10MB")
    , callbackUrl = "http://absolute.callback.url", expiryDate = DateTime.now().plusDays(1), metadata = Map("anything" -> JsString("the caller wants to add to the envelope")))
  )).getBytes



  def withEnvelope: Future[FileUploadSupport] = {
    WS
      .url(url)
      .withHeaders("Content-Type" -> "application/json")
      .post(payload)
      .flatMap{ resp =>
        val id = resp.header("Location").map{ _.split("/").last }.get
        getEnvelopeFor(id)
      }
  }

  def getEnvelopeFor(id: String): Future[FileUploadSupport] = {
    WS
      .url(s"$url/$id")
      .get()
      .map{ resp =>
        val envelope = Json.fromJson[Envelope](resp.json).get
        self.mayBeEnvelope = Some(envelope)
        self
      }
  }

  def refresh: Future[FileUploadSupport] = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    getEnvelopeFor(mayBeEnvelope.get._id)
  }

  def doUpload(data: Array[Byte], filename: String = "test.data"): Future[WSResponse] = {
    require(mayBeEnvelope.isDefined, "No envelope defined")
    WS
      .url(s"$url/${mayBeEnvelope.get._id}/file/$filename/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
  }


}
