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

package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.read.routing.FileTransferNotification


trait FakePushService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val pushServicePort = 8901

  private lazy val path = "/notification/fileready"

  private lazy val server = new WireMockServer(wireMockConfig().port(pushServicePort))

  lazy val pushServiceUrl: String = s"http://localhost:$pushServicePort$path"

  override def beforeAll() = {
    super.beforeAll()
    server.start()
  }

  override def afterAll() = {
    super.afterAll()
    server.stop()
  }

  def stubPushEndpoint(status: Int = 204) =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status))
    )

  def verifyPushNotification(fileTransferNotification: FileTransferNotification): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(path))
        .withHeader("Content-Type", containing("application/json"))
        .withRequestBody(equalToJson {
          implicit val ftnf = FileTransferNotification.format
          Json.toJson(fileTransferNotification).toString
        })
    )
}
