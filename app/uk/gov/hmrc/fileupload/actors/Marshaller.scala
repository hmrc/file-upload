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


object Marshaller {
	case class Marshall(obj: Any)
	case class UnMarshall(json: JsValue, toType: Class[_])

	def props = Props[Marshaller]
}

class Marshaller extends Actor{
	import Marshaller._
	import Envelope._

	override def preRestart(reason: Throwable, message: Option[Any]) = {
		sender ! reason
		super.preRestart(reason, message)
	}

	def receive = {
		case Marshall(obj) => sender ! toJson(obj)
		case json UnMarshall toType => sender ! fromJson(json, toType)
	}

	def toJson(any: Any): JsValue = any match {
		case  obj if obj.isInstanceOf[Envelope] => Json.toJson[Envelope](obj.asInstanceOf[Envelope])
	}

	def fromJson(json: JsValue, toType: Class[_]): Any = toType match {
		case  aType if aType == classOf[Envelope] => Json.fromJson[Envelope](json).get
	}

}
