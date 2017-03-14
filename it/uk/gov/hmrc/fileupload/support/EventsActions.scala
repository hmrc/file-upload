package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.write.envelope.Formatters._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, MarkFileAsInfected, QuarantineFile, StoreFile}

trait EventsActions extends ActionsSupport {
  this: Suite =>

  def sendCommandQuarantineFile(e: QuarantineFile): WSResponse =
    client
      .url(s"$url/commands/quarantine-file")
      .post(Json.toJson(e))
      .futureValue

  def sendCommandMarkFileAsClean(e: MarkFileAsClean): WSResponse =
    client
      .url(s"$url/commands/mark-file-as-clean")
      .post(Json.toJson(e))
      .futureValue

  def sendCommandMarkFileAsInfected(e: MarkFileAsInfected): WSResponse =
    client
      .url(s"$url/commands/mark-file-as-infected")
      .post(Json.toJson(e))
      .futureValue

  def sendCommandStoreFile(e: StoreFile): WSResponse =
    client
      .url(s"$url/commands/store-file")
      .post(Json.toJson(e))
      .futureValue
}
