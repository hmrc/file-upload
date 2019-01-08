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

package uk.gov.hmrc.fileupload.utils

import akka.util.ByteString
import play.api.data.validation.ValidationError
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.libs.streams.Accumulator
import play.api.mvc.{BodyParser, RequestHeader, Result}
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object JsonUtils {
  def oFormat[A](format: Format[A]): OFormat[A] = new OFormat[A] {
    def reads(json: JsValue): JsResult[A] = format.reads(json)

    def writes(o: A): JsObject = format.writes(o).as[JsObject]
  }

  def optional[A : Writes](k: String, value: Option[A]): JsObject =
    value.map(v => Json.obj(k -> v)).getOrElse(Json.obj())

  def jsonBodyParser[A : Reads](implicit ec: ExecutionContext): BodyParser[A] = new BodyParser[A] {
    def apply(v1: RequestHeader): Accumulator[ByteString, Either[Result, A]] = {
      StreamUtils.iterateeToAccumulator(Iteratee.consume[Array[Byte]]()).map { data =>
        Try(Json.parse(data).validate[A]) match {
          case Success(JsSuccess(a, _)) => Right(a)
          case Success(JsError(errors)) => Left(ExceptionHandler(400, flattenValidationErrors(errors).mkString))
          case Failure(NonFatal(ex)) => Left(ExceptionHandler(ex))
        }
      }
    }
  }

  def flattenValidationErrors(errors: Seq[(JsPath, Seq[ValidationError])]) =
    errors.foldLeft(new StringBuilder) { (obj, error) =>
      obj append s"${error._1.toJsonString} --> ${error._2.head.messages.flatten.mkString}"
    }
}

object errorAsJson {
  def apply(msg: String) = JsObject(Seq("error" -> Json.obj("msg" -> msg)))
}
