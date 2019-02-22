/*
 * Copyright 2019 HM Revenue & Customs
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

import uk.gov.hmrc.fileupload.controllers.GetFileMetadataReport
import uk.gov.hmrc.play.test.UnitSpec


class HrefSpec extends UnitSpec{

  "Href generation" should {
    "handle percentage" in {
      val fileIdTestCase = "Scan+15+Jun+2017%2c+13.04.pdf"
      val encodedProperly = "Scan+15+Jun+2017%25252c+13.04.pdf"
      val x = GetFileMetadataReport.href(EnvelopeId("e"), FileId(fileIdTestCase))
       x should endWith(s"/envelopes/e/files/$encodedProperly/content")
    }
    "handle all special characters" in {
      val fileIdTestCase = "ˮ깉ീ"
      val encodedProperly = "%25CB%25AE%25EA%25B9%2589%25E0%25B5%2580"
      GetFileMetadataReport
        .href(EnvelopeId("e"), FileId(fileIdTestCase)) should endWith(s"/envelopes/e/files/$encodedProperly/content")
    }
  }
}
