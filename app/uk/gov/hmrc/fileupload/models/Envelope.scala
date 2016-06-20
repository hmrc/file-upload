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

package uk.gov.hmrc.fileupload.models

import org.joda.time.DateTime
import play.api.libs.json._

case class Envelope(_id: String, constraints: Constraints, callbackUrl: String, expiryDate: DateTime, metadata: Map[String, JsValue], files: Option[Seq[String]] = None ) {
	require(!isExpired(), "expiry date cannot be in the past")

	def isExpired(): Boolean = expiryDate.isBeforeNow

	def contains(file: String) = files match {
		case Some(f) => f.contains(file)
		case None => false
	}
}

case class Constraints(contentTypes: Seq[String], maxItems: Int, maxSize: String, maxSizePerItem: String ) {

	validateSizeFormat("maxSize",  maxSize )
	validateSizeFormat( "maxSizePerItem", maxSizePerItem )

	def validateSizeFormat(name: String, value: String) = {
		val pattern = "[0-9]+(KB|MB|GB|TB|PB)".r
		if(pattern.findFirstIn(value).isEmpty) throw new ValidationException(s"$name has an invalid size format ($value)")
	}



}

object Envelope {

	implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
	implicit val constraintsReads: Format[Constraints] = Json.format[Constraints]
	implicit val envelopeReads: Format[Envelope] = Json.format[Envelope]

	def fromJson(json: JsValue, _id: String, maxTTL: Int): Envelope = {
	  val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id )
	  val envelope = Json.fromJson[Envelope](rawData).get
		val maxExpiryDate: DateTime = DateTime.now().plusDays(maxTTL)

		envelope.expiryDate.isAfter(maxExpiryDate) match {
			case true => envelope.copy(expiryDate = maxExpiryDate)
			case false => envelope
		}

  }

}
