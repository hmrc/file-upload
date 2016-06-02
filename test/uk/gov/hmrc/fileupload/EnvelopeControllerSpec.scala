/*
 * Copyright 2016 HM Revenue & Customs
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

import uk.gov.hmrc.fileupload.controllers.EnvelopeController
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import play.api.http.Status
import play.api.test.FakeRequest

class EnvelopeControllerSpec  extends UnitSpec with WithFakeApplication {



  "create envelope with a description" should {
    "return response with OK status" in {
      val result = EnvelopeController.create()
      result.apply()
      status(result) shouldBe Status.OK
      result.body.id shouldBe notNull
    }
  }

}
