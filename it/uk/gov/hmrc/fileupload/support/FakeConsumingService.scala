/*
 * Copyright 2021 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FakeConsumingService extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val consumingServicePort = 8900

  private lazy val server = new WireMockServer(wireMockConfig().port(consumingServicePort))

  final lazy val consumingServiceBaseUrl = s"http://localhost:$consumingServicePort"

  override protected def beforeAll() = {
    super.beforeAll()
    server.start()
  }

  override protected def afterAll() = {
    super.afterAll()
    server.stop()
  }

  def stubCallback(callbackPath: String = "mycallbackpath") =
    server.stubFor(
      WireMock.get(urlEqualTo(s"/$callbackPath"))
        .willReturn(WireMock.aResponse().withStatus(200))
    )

  def verifyQuarantinedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit =
    happyCallbackEvent(callbackPath, envelopeId, fileId, "QUARANTINED")

  def verifyNoVirusDetectedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit =
    happyCallbackEvent(callbackPath, envelopeId, fileId, "CLEANED")

  def verifyVirusDetectedCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit =
    sadCallbackEvent(callbackPath, envelopeId, fileId, "ERROR", "VirusDetected")

  def verifyAvailableCallbackReceived(callbackPath: String = "mycallbackpath", envelopeId: EnvelopeId, fileId: FileId): Unit =
    happyCallbackEvent(callbackPath, envelopeId, fileId, "AVAILABLE")

  private def happyCallbackEvent(callbackPath: String, envelopeId: EnvelopeId, fileId: FileId, status: String): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(s"/$callbackPath"))
        .withHeader("Content-Type", containing("application/json"))
        .withRequestBody(equalToJson(s"""{"envelopeId": "$envelopeId", "fileId": "$fileId", "status": "$status"}"""))
    )

  private def sadCallbackEvent(callbackPath: String, envelopeId: EnvelopeId, fileId: FileId, status: String, reason: String): Unit =
    server.verify(
      postRequestedFor(urlEqualTo(s"/$callbackPath"))
        .withHeader("Content-Type", containing("application/json"))
        .withRequestBody(equalToJson(
          s"""{"envelopeId": "$envelopeId", "fileId": "$fileId", "status": "$status", "reason": "$reason"}"""
        ))
    )

  def callbackUrl(callbackPath: String = "mycallbackpath") =
    s"$consumingServiceBaseUrl/$callbackPath"
}
