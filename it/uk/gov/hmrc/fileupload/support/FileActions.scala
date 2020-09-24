package uk.gov.hmrc.fileupload.support

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalatest.TestSuite
import play.api.http.HeaderNames
import play.api.libs.ws.WSResponse
import play.utils.UriEncoding
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

trait FileActions extends ActionsSupport {
  this: TestSuite =>

  def basic644(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def urlEncode(fileId: FileId) = UriEncoding.encodePathSegment(fileId.value, "UTF-8")


  def upload(data: Array[Byte], envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/$fileId/$fileRefId")
      .withHttpHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def delete(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/${urlEncode(fileId)}")
      .delete()
      .futureValue

  def download(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/${urlEncode(fileId)}/content")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic644("yuan:yaunspassword")))
      .get()
      .futureValue

  def getFileMetadataFor(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/${urlEncode(fileId)}/metadata")
      .get()
      .futureValue

  def downloadEnvelope(envelopeId: EnvelopeId): WSResponse =
    client
      .url(s"$fileTransferUrl/envelopes/$envelopeId")
      .get()
      .futureValue
}
