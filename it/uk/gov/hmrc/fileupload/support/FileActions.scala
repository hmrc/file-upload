package uk.gov.hmrc.fileupload.support

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}

trait FileActions extends ActionsSupport {

  def upload(data: Array[Byte], envelopeId: String, fileId: String): WSResponse =
    WS
      .url(s"$url/envelope/$envelopeId/file/$fileId/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def download(envelopeId: String, fileId: String): WSResponse =
    WS
      .url(s"$url/envelope/$envelopeId/file/$fileId/content")
      .get()
      .futureValue

  def updateFileMetadata(data: String, envelopeId: String, fileId: String): WSResponse =
    WS
      .url(s"$url/envelope/$envelopeId/file/$fileId/metadata" )
      .withHeaders("Content-Type" -> "application/json")
      .put(data.getBytes)
      .futureValue

  def getFileMetadataFor(envelopeId: String, fileId: String): WSResponse =
    WS
      .url(s"$url/envelope/$envelopeId/file/$fileId/metadata")
      .get()
      .futureValue
}
