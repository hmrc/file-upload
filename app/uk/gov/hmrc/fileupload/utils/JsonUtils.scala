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

package uk.gov.hmrc.fileupload.utils

import play.api.libs.json._

object JsonUtils {
  def oFormat[A](format: Format[A]): OFormat[A] = new OFormat[A] {
    def reads(json: JsValue): JsResult[A] = format.reads(json)

    def writes(o: A): JsObject = format.writes(o).as[JsObject]
  }

  def optional[A : Writes](k: String, value: Option[A]): JsObject =
    value.map(v => Json.obj(k -> v)).getOrElse(Json.obj())

  def flattenValidationErrors(errors: Seq[(JsPath, Seq[JsonValidationError])]) =
    errors.foldLeft(new StringBuilder) { (obj, error) =>
      obj.append(s"${error._1.toJsonString} --> ${error._2.head.messages.flatten.mkString}")
    }
}

object errorAsJson {
  def apply(msg: String) = JsObject(Seq("error" -> Json.obj("msg" -> msg)))
}
