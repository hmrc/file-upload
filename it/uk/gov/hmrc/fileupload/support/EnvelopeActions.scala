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

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalatest.TestSuite
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.EnvelopeId

trait EnvelopeActions extends ActionsSupport {
  this: TestSuite =>

  def basic64(s: String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse =
    client
      .url(s"$url/envelopes")
      .withHttpHeaders("Content-Type" -> "application/json")
      .post(data)
      .futureValue

  def createEnvelopeWithId(id: String, data: String): WSResponse = createEnvelopeWithId(id, data.getBytes())

  def createEnvelopeWithId(id: String, data: Array[Byte]): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .withHttpHeaders("Content-Type" -> "application/json")
      .put(data)
      .futureValue

  def getEnvelopeFor(id: EnvelopeId): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .get()
      .futureValue

  def envelopeIdFromHeader(response: WSResponse): EnvelopeId = {
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def createEnvelope(): EnvelopeId = {
    val response: WSResponse = createEnvelope(EnvelopeReportSupport.requestBodyAsJson().toString)
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def deleteEnvelopFor(id: EnvelopeId): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .delete()
      .futureValue

  def deleteEnvelopWithWrongAuth(id: EnvelopeId): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yua:yaunspassword")))
      .delete()
      .futureValue

  def submitRoutingRequest(envelopeId: EnvelopeId, destination: String, application: String = "testApplication"): WSResponse =
    client
      .url(s"$fileRoutingUrl/requests")
      .post(
         Json.obj(
           "envelopeId"  -> envelopeId,
           "destination" -> destination,
           "application" -> application
        )
      )
      .futureValue

  def getEnvelopesForDestination(destination: Option[String]): WSResponse = {
    client
      .url(s"$fileTransferUrl/envelopes${destination.map(d => s"?destination=$d").getOrElse("")}")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .get()
      .futureValue
  }

  def getEnvelopesForStatus(status: List[String], inclusive: Boolean) = {
    val statuses = status.map(n => s"status=$n").mkString("&")
    client
      .url(s"$url/envelopes?$statuses&inclusive=$inclusive")
      .withHttpHeaders(HeaderNames.AUTHORIZATION -> ("Basic " + basic64("yuan:yaunspassword")))
      .get()
      .futureValue
  }

  def archiveEnvelopFor(id: EnvelopeId): WSResponse =
    client
      .url(s"$fileTransferUrl/envelopes/$id")
      .delete()
      .futureValue

  def getInProgressFiles(): WSResponse =
    client
      .url(s"$url/files/inprogress")
      .get()
      .futureValue
}
