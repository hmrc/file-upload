package uk.gov.hmrc.fileupload.support

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}

trait EventsActions extends ActionsSupport {

  def sendFileInQuarantineStored(e: FileInQuarantineStored): WSResponse =
    WS
      .url(s"$url/events/${e.getClass.getSimpleName.toLowerCase}")
      .post(EventsSupport.fileInQuarantineStoredRequestBodyAsJson(e))
      .futureValue

  def sendFileScanned(e: FileScanned): WSResponse =
    WS
      .url(s"$url/events/${e.getClass.getSimpleName.toLowerCase}")
      .post(EventsSupport.fileScannedRequestBodyAsJson(e))
      .futureValue
}
