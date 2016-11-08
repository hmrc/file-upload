package uk.gov.hmrc.fileupload.support

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import play.api.Play.current
import play.api.http.HeaderNames
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

trait FileActions extends ActionsSupport {

  def basic644(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

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
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content").withHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic644("yuan:yaunspassword")))
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
      .url(s"$fileTransferUrl/envelopes/$envelopeId")
      .get()
      .futureValue

  def deleteFileForFileRef(fileRefId: FileRefId): WSResponse =
    WS
      .url(s"$fileInProgress/$fileRefId")
      .delete()
      .futureValue
}
