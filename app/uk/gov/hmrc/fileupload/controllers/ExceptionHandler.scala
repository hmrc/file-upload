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
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.fileupload.models.ValidationException

object ExceptionHandler{

	def apply[T <: Throwable](exception: T): Result = exception match {
		case e : ValidationException => ValidationExceptionHandler(e)
		case e : NoSuchElementException => NoSuchElementHandler(e)
		case e : BadRequestException => BadRequestHandler(e)
		case e : Throwable => DefaultExceptionHandler(e)
	}

}

sealed trait ExceptionHandler[T <: Throwable] {

	def apply(exception: T): Result

}


object ValidationExceptionHandler extends ExceptionHandler[ValidationException]{

	def apply(exception: ValidationException): Result = {
		val response: JsObject = Json.obj("reason" -> exception.reason)
		Result(ResponseHeader(BAD_REQUEST), Enumerator( Json.stringify(response).getBytes ))
	}
}

object NoSuchElementHandler extends ExceptionHandler[NoSuchElementException]{

	def apply(exception: NoSuchElementException): Result = {
		val response: JsObject = Json.obj("reason" -> "invalid json format")
		Result(ResponseHeader(BAD_REQUEST), Enumerator( Json.stringify(response).getBytes ))
	}
}

object BadRequestHandler extends ExceptionHandler[BadRequestException]{
	override def apply(exception: BadRequestException): Result = {
		val response: JsObject = Json.obj("reason" -> exception.getMessage)
		Result(ResponseHeader(BAD_REQUEST), Enumerator( Json.stringify(response).getBytes ))
	}
}

object DefaultExceptionHandler extends ExceptionHandler[Throwable]{
	override def apply(exception: Throwable): Result = {
		val response: JsObject = Json.obj("reason" -> "Internal Server Error")
		Result(ResponseHeader(INTERNAL_SERVER_ERROR), Enumerator( Json.stringify(response).getBytes ))
	}
}
