/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.mvc.PathBindable
import play.utils.UriEncoding
import uk.gov.hmrc.play.test.UnitSpec


class UrlBindersSpec extends UnitSpec{

  "FileId PathBinder" should {
    "handle all special characters" in {
      val suspect: PathBindable[FileId] = FileId.urlBinder

      val fileIdTestCase = "Scan+15+Jun+2017%2c+13.04.pdf"
      val encodedProperly = "Scan+15+Jun+2017%252c+13.04.pdf"
      val given = suspect.unbind("any", FileId(fileIdTestCase))

      given shouldEqual encodedProperly
    }
  }
}
