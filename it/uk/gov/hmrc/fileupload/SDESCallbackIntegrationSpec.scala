/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload

import java.time.Instant

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.routing.{FileReceived, MD5, Notification, NotificationItem}
import uk.gov.hmrc.fileupload.support.{ActionsSupport, IntegrationSpec}

class SDESCallbackIntegrationSpec extends IntegrationSpec with ActionsSupport {

  Feature("SDES Callbacks") {
    Scenario("handle SDES callbacks and return OK") {
      val item = notificationItem(FileReceived)
      val response =
        client
          .url(s"$fileRoutingUrl/sdes-callback")
          .post(Json.toJson(item))
          .futureValue

      response.status shouldBe OK
    }

    Scenario("return 400 Bad Request if JSON cannot be parsed") {
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
