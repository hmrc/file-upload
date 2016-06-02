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
import play.api.libs.json.{Json, Reads}
import reactivemongo.bson.BSONObjectID

case class Envelope(constraints: Constraints, callbackUrl: String, expiryDate: DateTime, metadata: Map[String, String] ){
}

case class Constraints(contentTypes: Seq[String], maxItems: Int, maxSize: String, maxSizePerItem: String )

object Envelope {
  val id = BSONObjectID.generate
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsReads = Json.format[Constraints]
  implicit val envelopeReads = Json.format[Envelope]

}
