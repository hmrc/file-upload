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
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.mvc.{Result, RequestHeader, BodyParser}
import uk.gov.hmrc.fileupload.envelope.{Constraints, Envelope, File}

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class EnvelopeReport(id: Option[String] = None,
                          constraints: Option[ConstraintsReport] = None,
                          callbackUrl: Option[String] = None,
                          expiryDate: Option[DateTime] = None,
                          metadata: Option[Map[String, JsValue]] = None,
                          status: Option[String] = None,
                          files: Option[Seq[File]] = None)

case class ConstraintsReport(contentTypes: Option[Seq[String]] = None,
                             maxItems: Option[Int] = None,
                             maxSize: Option[String] = None,
                             maxSizePerItem: Option[String] = None)

object EnvelopeReport {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileReads: Format[File] = Json.format[File]

  implicit val createConstraintsReads: Format[ConstraintsReport] = Json.format[ConstraintsReport]
  implicit val createEnvelopeReads: Format[EnvelopeReport] = Json.format[EnvelopeReport]

  val MAX_ITEMS_DEFAULT = 1

  def toEnvelope(envelopeId: String, report: EnvelopeReport): Envelope =
    Envelope(
      _id = envelopeId,
      constraints = report.constraints.map(toConstraints),
      callbackUrl = report.callbackUrl,
      expiryDate = report.expiryDate,
      metadata = report.metadata,
      files = report.files)

  def fromEnvelope(envelope: Envelope): EnvelopeReport =
    EnvelopeReport(
      id = Some(envelope._id),
      constraints = envelope.constraints.map(fromConstraints),
      callbackUrl = envelope.callbackUrl,
      expiryDate = envelope.expiryDate,
      metadata = envelope.metadata,
      status = Option(envelope.status).map(_.toString),
      files = envelope.files)

  private def toConstraints(report: ConstraintsReport): Constraints = {
    val maxItems: Int = report.maxItems.getOrElse[Int](MAX_ITEMS_DEFAULT)
    Constraints(contentTypes = report.contentTypes, maxItems = Some(maxItems), maxSize = report.maxSize, maxSizePerItem = report.maxSizePerItem)
  }

  private def fromConstraints(constraints: Constraints): ConstraintsReport =
    ConstraintsReport(
      contentTypes = constraints.contentTypes,
      maxItems = constraints.maxItems,
      maxSize = constraints.maxSize,
      maxSizePerItem = constraints.maxSizePerItem)
}

object EnvelopeParser extends BodyParser[EnvelopeReport] {

  def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, EnvelopeReport]] = {
    import EnvelopeReport._

    Iteratee.consume[Array[Byte]]().map { data =>
      Try(Json.fromJson[EnvelopeReport](Json.parse(data)).get) match {
        case Success(report) => Right(report)
        case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
      }
    }(ExecutionContext.global)
  }
}

