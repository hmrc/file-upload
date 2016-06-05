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
import play.api.libs.json.{Json, Reads, Format}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.controllers.{ConstraintsTransferObject, EnvelopeTransferObject}
import uk.gov.hmrc.mongo.json.BSONObjectIdFormats._

case class Envelope(_id: BSONObjectID, constraints: Constraints, callbackUrl: String, expiryDate: DateTime, metadata: Map[String, String] )

case class Constraints(contentTypes: Seq[String], maxItems: Int, maxSize: String, maxSizePerItem: String )

object Envelope {
  def makeEnvelope(envelopeTO: EnvelopeTransferObject): Envelope = {
    val constraintsTO: ConstraintsTransferObject = envelopeTO.constraints
    val constraints = new Constraints( contentTypes = constraintsTO.contentTypes, maxItems = constraintsTO.maxItems, maxSize = constraintsTO.maxSize, maxSizePerItem = constraintsTO.maxSizePerItem)
    new Envelope(_id = BSONObjectID.generate, constraints = constraints, callbackUrl = envelopeTO.callbackUrl, expiryDate = envelopeTO.expiryDate, metadata = envelopeTO.metadata)
  }

  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsReads: Format[Constraints] = Json.format[Constraints]
  implicit val envelopeReads: Format[Envelope] = Json.format[Envelope]
}
