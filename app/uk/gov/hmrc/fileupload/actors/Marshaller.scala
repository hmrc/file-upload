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

package uk.gov.hmrc.fileupload.actors

import akka.actor.{Props, Actor}
import play.api.libs.json.{Json, JsValue}
import uk.gov.hmrc.fileupload.models.Envelope
import scala.util.Try
import Marshaller._
import Envelope._

object Marshaller {

  case class Marshall(obj: Any)

  case class UnMarshall(json: JsValue, toType: Class[_])

  def props = Props[Marshaller]
}

class Marshaller extends Actor {

  def receive = {
    case Marshall(obj) => sender ! toJson(obj)    // TODO need to update Envelope Service and Controller
    case e: Envelope => sender ! toJson(e)
    case json UnMarshall toType => sender ! fromJson(json, toType)
  }

  def toJson(any: Any): Try[JsValue] = any match {
    case obj if obj.isInstanceOf[Envelope] => Try(Json.toJson[Envelope](obj.asInstanceOf[Envelope]))
  }

  def toJson(envelope: Envelope): Try[JsValue] = Try(Json.toJson[Envelope](envelope.asInstanceOf[Envelope]))

  def fromJson(json: JsValue, toType: Class[_]): Try[Envelope] = toType match {
    case aType if aType == classOf[Envelope] => Try(Json.fromJson[Envelope](json).get)
  }

}
