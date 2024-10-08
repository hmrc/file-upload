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
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status
import uk.gov.hmrc.fileupload.IntegrationTestApplicationComponents

trait ActionsSupport
   extends ScalaFutures
      with Status
      with IntegrationTestApplicationComponents
      with IntegrationPatience {
  this: TestSuite =>

  lazy val url = s"http://localhost:$port/file-upload"
  lazy val fileTransferUrl = s"http://localhost:$port/file-transfer"
  lazy val fileRoutingUrl = s"http://localhost:$port/file-routing"
  val client = play.api.test.WsTestClient.InternalWSClient(scheme = "http", port = -1)
}
