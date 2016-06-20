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

case class Envelope(_id: String, constraints: Option[Constraints] = None, callbackUrl: Option[String] = None, expiryDate: Option[DateTime] = None, metadata: Option[Map[String, JsValue]] = None, files: Option[Seq[String]] = None ) {
	require(!isExpired, "expiry date cannot be in the past")

	def isExpired: Boolean = expiryDate match {
		case Some(dateTime) => dateTime.isBeforeNow
		case None => false
	}

	def contains(file: String) = files match {
		case Some(f) => f.contains(file)
		case None => false
	}
}

case class Constraints(contentTypes: Option[Seq[String]] = None, maxItems: Option[Int] = None, maxSize: Option[String] = None, maxSizePerItem: Option[String] = None ) {

	maxSize.foreach( validateSizeFormat("maxSize",  _ ) )
  maxSizePerItem.foreach( validateSizeFormat( "maxSizePerItem", _ ) )

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

    val expiryDate = envelope.expiryDate.map( d => if(d.isBefore(maxExpiryDate)) d else maxExpiryDate )
    envelope.copy(expiryDate = expiryDate)
  }

}
