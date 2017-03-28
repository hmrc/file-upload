package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}

object EventsSupport extends Support {

  def fileInQuarantineStoredRequestBodyAsJson(e: FileInQuarantineStored) = Json.parse(fileInQuarantineStoredRequestBody(e))

  def fileInQuarantineStoredRequestBody(e: FileInQuarantineStored) =
    s"""
       |{
       |  "envelopeId": "${e.envelopeId.value}",
       |	"fileId": "${e.fileId.value}",
       |	"fileRefId": "${e.fileRefId.value}",
       |	"created": ${e.created},
       |	"name": "${e.name}",
       |	"contentType": "${e.contentType}",
       |  "fileLength": ${e.fileLength.get},
       |	"metadata": ${Json.stringify(e.metadata)}
       |}
		 """.stripMargin

  def fileScannedRequestBodyAsJson(e: FileScanned) = Json.parse(fileScannedRequestBody(e))

  def fileScannedRequestBody(e: FileScanned) =
    s"""
       |{
       |  "envelopeId": "${e.envelopeId}",
       |	"fileId": "${e.fileId}",
       |	"fileRefId": "${e.fileRefId}",
       |	"hasVirus": ${e.hasVirus}
       |}
		 """.stripMargin
}
