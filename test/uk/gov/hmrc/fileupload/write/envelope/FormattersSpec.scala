/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.write.envelope

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.EnvelopeId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class FormattersSpec extends AnyWordSpecLike with Matchers {

  import Formatters._

  "Formatters" should {
    "read current representation of EnvelopeRouted" in {
	  Json.parse("""{"id": "1", "isPushed": true}""").as[EnvelopeRouted] shouldBe EnvelopeRouted(id = EnvelopeId("1"), isPushed = true)
    }

    "read previous representations of EnvelopeRouted" in {
      Json.parse("""{"id": "1"}""").as[EnvelopeRouted] shouldBe EnvelopeRouted(id = EnvelopeId("1"), isPushed = false)
    }
  }
}
