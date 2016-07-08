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
import play.api.libs.json._
import uk.gov.hmrc.fileupload.envelope.{Constraints, Envelope, File}

case class EnvelopeReport(id: Option[String] = None, constraints: Option[ConstraintsReport] = None, callbackUrl: Option[String] = None,
                          expiryDate: Option[DateTime] = None, metadata: Option[Map[String, JsValue]] = None, status: Option[String] = None, files: Option[Seq[File]] = None)

case class ConstraintsReport(contentTypes: Option[Seq[String]] = None, maxItems: Option[Int] = None, maxSize: Option[String] = None, maxSizePerItem: Option[String] = None)

object EnvelopeReport {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileReads: Format[File] = Json.format[File]

  implicit val createConstraintsReads: Format[ConstraintsReport] = Json.format[ConstraintsReport]
  implicit val createEnvelopeReads: Format[EnvelopeReport] = Json.format[EnvelopeReport]

  val MAX_ITEMS_DEFAULT = 1

  def from(createEnvelope: Option[EnvelopeReport]): Envelope =
    createEnvelope.map(fromCreateEnvelope).getOrElse(Envelope.emptyEnvelope())

  def fromCreateEnvelope(dto: EnvelopeReport) =
    Envelope.emptyEnvelope().copy(constraints = dto.constraints.map(fromCreateConstraints), callbackUrl = dto.callbackUrl, expiryDate = dto.expiryDate, metadata = dto.metadata, files = None)

  def toCreateEnvelope(envelope: Envelope) = {
    val createConstraints = envelope.constraints.map ( constraint =>  ConstraintsReport(constraint.contentTypes, constraint.maxItems, constraint.maxSize, constraint.maxSizePerItem ) )
    EnvelopeReport(Some(envelope._id), createConstraints, envelope.callbackUrl, envelope.expiryDate, envelope.metadata, Some(envelope.status.toString.toUpperCase()), envelope.files)
  }

  private def fromCreateConstraints(dto: ConstraintsReport): Constraints = {
    val maxItems: Int = dto.maxItems.getOrElse[Int](MAX_ITEMS_DEFAULT)
    Envelope.emptyConstraints().copy(dto.contentTypes, Some(maxItems), dto.maxSize, dto.maxSizePerItem)
  }
}

