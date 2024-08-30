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

import org.scalatest.TestSuite
import play.api.libs.json.Json
import play.api.libs.ws.{WSResponse, writeableOf_JsValue}
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}
import uk.gov.hmrc.fileupload.write.envelope.Formatters.given
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
