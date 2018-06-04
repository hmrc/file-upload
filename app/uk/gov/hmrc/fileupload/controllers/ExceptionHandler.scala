/*
 * Copyright 2018 HM Revenue & Customs
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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import play.api.Logger
import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{ResponseHeader, Result}
import uk.gov.hmrc.fileupload.read.envelope.ValidationException
import uk.gov.hmrc.http.BadRequestException

object ExceptionHandler {

  def apply[T <: Throwable](exception: T): Result = exception match {
    case e: ValidationException => IllegalArgumentHandler(e)
    case e: NoSuchElementException => NoSuchElementHandler(e)
    case e: BadRequestException => BadRequestHandler(e)
    case e: JsonParseException => BadRequestHandler(new BadRequestException(s"Malformed json: ${e.getMessage}"))
    case e: IllegalArgumentException => BadRequestHandler(new BadRequestException(s"${e.getMessage}"))
    case e: JsonMappingException => BadRequestHandler(new BadRequestException(s"${e.getMessage}, i.e. these is not request body"))
    case e: Throwable => DefaultExceptionHandler(e)
  }

  def apply(statusCode: Int, responseMessage: String): Result = {
    val response: JsObject = JsObject(Seq("error" -> Json.obj("msg" -> responseMessage)))
    val source = Source.single(ByteString.fromArray(Json.stringify(response).getBytes))
    Result(ResponseHeader(statusCode), HttpEntity.Streamed(source, None, None))
  }
}

sealed trait ExceptionHandler[T <: Throwable] {
  def apply(exception: T): Result
}

object IllegalArgumentHandler extends ExceptionHandler[IllegalArgumentException] {
  def apply(exception: IllegalArgumentException): Result = {
    ExceptionHandler(BAD_REQUEST, exception.getMessage)
  }
}

object NoSuchElementHandler extends ExceptionHandler[NoSuchElementException] {
  def apply(exception: NoSuchElementException): Result = {
    val message = "Invalid json format"
    Logger.warn(message, exception)
    ExceptionHandler(BAD_REQUEST, message)
  }
}

object BadRequestHandler extends ExceptionHandler[BadRequestException] {
  override def apply(exception: BadRequestException): Result = {
    ExceptionHandler(BAD_REQUEST, exception.getMessage)
  }
}

object DefaultExceptionHandler extends ExceptionHandler[Throwable] {
  override def apply(exception: Throwable): Result = {
    val message = "Internal Server Error"
    Logger.warn(message, exception)
    ExceptionHandler(INTERNAL_SERVER_ERROR, message)
  }
}
