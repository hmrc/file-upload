package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}

trait EventsActions extends ActionsSupport {
  this: Suite =>

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
