/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.routing.ZipData
import play.api.libs.json.{Json, Writes}

trait FakeFrontendService extends BeforeAndAfterAll {
  this: Suite =>

  lazy val mockFEServicePort = 8017

  lazy val mockFEServer = WireMockServer(wireMockConfig().port(mockFEServicePort))

  override def beforeAll() = {
    super.beforeAll()
    mockFEServer.start()
  }

  override def afterAll() = {
    super.afterAll()
    mockFEServer.stop()
  }

  def stubZipEndpoint(envelopeId: EnvelopeId, result: Either[Int, ZipData]) =
    mockFEServer.stubFor(
      post(urlEqualTo(s"/internal-file-upload/zip/envelopes/${envelopeId.value}"))
        .willReturn(
          result match {
            case Left(status) => aResponse().withStatus(status)
            case Right(zipData) => given Writes[ZipData] = ZipData.format
                                   aResponse()
                                     .withStatus(200)
                                     .withBody(Json.toJson(zipData).toString)
          }
        )
    )
}
