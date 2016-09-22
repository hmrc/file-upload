package uk.gov.hmrc.fileupload.support

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

trait FileActions extends ActionsSupport {

  def upload(data: Array[Byte], envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/$fileRefId")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def delete(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId")
      .delete()
      .futureValue

  def download(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content")
      .get()
      .futureValue

  def updateFileMetadata(data: String, envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata" )
      .withHeaders("Content-Type" -> "application/json")
      .put(data.getBytes)
      .futureValue

  def getFileMetadataFor(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .get()
      .futureValue

  def downloadEnvelope(envelopeId: EnvelopeId): WSResponse =
    WS
      .url(s"$fileTransferUrl/non-stub/envelopes/$envelopeId")
      .get()
      .futureValue
}
