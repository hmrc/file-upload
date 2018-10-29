package uk.gov.hmrc.fileupload.support

import org.scalatest.TestSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}
import uk.gov.hmrc.fileupload.write.envelope.Formatters._
import uk.gov.hmrc.fileupload.write.envelope._

trait EventsActions extends ActionsSupport {
  this: TestSuite =>

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

  def sendFileInQuarantineStored(e: FileInQuarantineStored): WSResponse =
    client
      .url(s"$url/events/${e.getClass.getSimpleName.toLowerCase}")
      .post(EventsSupport.fileInQuarantineStoredRequestBodyAsJson(e))
      .futureValue

  def sendFileScanned(e: FileScanned): WSResponse =
    client
      .url(s"$url/events/${e.getClass.getSimpleName.toLowerCase}")
      .post(EventsSupport.fileScannedRequestBodyAsJson(e))
      .futureValue
}
