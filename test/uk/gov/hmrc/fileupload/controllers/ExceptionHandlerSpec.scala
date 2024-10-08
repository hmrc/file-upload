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

package uk.gov.hmrc.fileupload.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json.parse
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.read.envelope.ValidationException
import uk.gov.hmrc.http.BadRequestException

import scala.concurrent.Future

class ExceptionHandlerSpec extends AnyWordSpecLike with Matchers {
  import uk.gov.hmrc.fileupload.Support.StreamImplicits.given

  "exception handler" should {
    "handle an unknown exception as an internal server error" in {
      object SomeException extends RuntimeException

      val result = Future.successful(ExceptionHandler(SomeException))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result) shouldBe parse("""{"error":{"msg":"Internal Server Error"}}""")
    }

    "handle a validation exception" in {
      val result = Future.successful(ExceptionHandler(ValidationException("someValidationException")))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe parse("""{"error":{"msg":"someValidationException"}}""")
    }

    "handle a bad request exception" in {
      val result = Future.successful(ExceptionHandler(BadRequestException("someBadRequest")))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result) shouldBe parse("""{"error":{"msg":"someBadRequest"}}""")
    }
  }
}
