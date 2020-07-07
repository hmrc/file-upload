package uk.gov.hmrc.fileupload

import java.time.Instant

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{FileProcessed, MD5, NotificationItem}
import uk.gov.hmrc.fileupload.support.{ActionsSupport, IntegrationSpec}

class SDESCallbackIntegrationSpec extends IntegrationSpec with ActionsSupport {

  feature("SDES Callbacks") {
    scenario("handle SDES callbacks and return OK") {
      val response =
        client
          .url(s"$fileRoutingUrl/sdes-callback")
          .post(Json.toJson(notificationItem))
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

  private def notificationItem =
    NotificationItem(
      notification = FileProcessed,
      informationType = Some("S18"),
      filename = "ourref.xyz.doc",
      checksumAlgorithm = MD5,
      checksum = "83HQWQ93D909Q0QWIJQE39831312EUIUQIWOEU398931293DHDAHBAS",
      availableUntil = Some(Instant.now()),
      failureReason = Some("We were unable to successfully do some action"),
      dateTime = Instant.now()
    )
}
