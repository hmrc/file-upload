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

package uk.gov.hmrc.fileupload.controllers

import play.api.http.Status._
import play.api.libs.json.Json.parse
import uk.gov.hmrc.fileupload.envelope.ValidationException
import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec

class ExceptionHandlerSpec extends UnitSpec {

  "exception handler" should {
    "handle an unknown exception as an internal server error" in {
			object SomeException extends RuntimeException

      val result = ExceptionHandler(SomeException)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      jsonBodyOf(result) shouldBe parse("""{"error":{"msg":"Internal Server Error"}}""")
    }

    "handle a validation exception" in {
      val result = ExceptionHandler(new ValidationException("someValidationException"))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe parse("""{"error":{"msg":"someValidationException"}}""")
    }

    "handle a bad request exception" in {
      val result = ExceptionHandler(new BadRequestException("someBadRequest"))

      status(result) shouldBe BAD_REQUEST
      jsonBodyOf(result) shouldBe parse("""{"error":{"msg":"someBadRequest"}}""")
    }
  }
}