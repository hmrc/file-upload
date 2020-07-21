package uk.gov.hmrc.fileupload

import java.time.Instant

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{FileProcessed, FileReceived, MD5, Notification, NotificationItem}
import uk.gov.hmrc.fileupload.support.{ActionsSupport, IntegrationSpec}

class SDESCallbackIntegrationSpec extends IntegrationSpec with ActionsSupport {

  feature("SDES Callbacks") {
    scenario("handle SDES callbacks and return OK") {
      val item = notificationItem(FileReceived)
      val response =
        client
          .url(s"$fileRoutingUrl/sdes-callback")
          .post(Json.toJson(item))
          .futureValue

      response.status shouldBe OK
    }

    scenario("return 400 Bad Request if JSON cannot be parsed") {
      val response =
        client
          .url(s"$fileRoutingUrl/sdes-callback")
          .post(Json.toJson("""{"missing": "values"}"""))
          .futureValue

      response.status shouldBe BAD_REQUEST
    }
  }

  private def notificationItem(notification: Notification) =
    NotificationItem(
      notification = notification,
      informationType = Some("S18"),
      filename = "ourref.xyz.doc",
      checksumAlgorithm = MD5,
      checksum = "83HQWQ93D909Q0QWIJQE39831312EUIUQIWOEU398931293DHDAHBAS",
      correlationId = "d1800c47-29b0-440a-9e2e-9d7362795e10",
      availableUntil = Some(Instant.now()),
      failureReason = Some("We were unable to successfully do some action"),
      dateTime = Instant.now()
    )
}
