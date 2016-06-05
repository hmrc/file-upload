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

import org.joda.time.DateTime
import play.api.libs.json.{Json, Reads}

case class EnvelopeTransferObject(constraints: ConstraintsTransferObject, callbackUrl: String, expiryDate: DateTime, metadata: Map[String, String] )

case class ConstraintsTransferObject(contentTypes: Seq[String], maxItems: Int, maxSize: String, maxSizePerItem: String )


object EnvelopeTransferObject {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsTransferObjectReads = Json.format[ConstraintsTransferObject]
  implicit val envelopeTransferObjectReads = Json.format[EnvelopeTransferObject]

}
