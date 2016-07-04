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

import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.fileupload.controllers.{CreateConstraints, CreateEnvelope}

sealed trait Status
case object Open extends Status
case object Sealed extends Status

object StatusWrites extends Writes[Status] {
  def writes(c: Status) = c match {
    case Sealed => Json.toJson("SEALED")
    case Open => Json.toJson("OPEN")
  }
}

object StatusReads extends Reads[Status] {
  def reads(value: JsValue) = value.as[String] match {
    case "SEALED" => JsSuccess( Sealed )
    case "OPEN" => JsSuccess( Open )
  }
}

case class Envelope(
                     _id: String,
                     constraints: Option[Constraints] = None,
                     callbackUrl: Option[String] = None, expiryDate: Option[DateTime] = None,
                     metadata: Option[Map[String, JsValue]] = None, files: Option[Seq[File]] = None,
                     status: Status = Open )
{
  def isSealed(): Boolean = this.status == Sealed

  require(!isExpired, "expiry date cannot be in the past")

  def isExpired: Boolean = expiryDate.exists(_.isBeforeNow)

  def contains(fileId: String) = files.exists(sequence => sequence.exists(_.id == fileId))
}

case class Constraints(contentTypes: Option[Seq[String]] = None, maxItems: Option[Int] = None, maxSize: Option[String] = None, maxSizePerItem: Option[String] = None) {

  maxSize.foreach(validateSizeFormat("maxSize", _))
  maxSizePerItem.foreach(validateSizeFormat("maxSizePerItem", _))

  def validateSizeFormat(name: String, value: String) = {
    val pattern = "[0-9]+(KB|MB|GB|TB|PB)".r
    if (pattern.findFirstIn(value).isEmpty) throw new ValidationException(s"$name has an invalid size format ($value)")
  }

}

case class File(rel: String = "file", href: String, id: String)

object Envelope {
  implicit val dateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val fileReads: Format[File] = Json.format[File]
  implicit val statusReads: Reads[Status] = StatusReads
  implicit val statusWrites: Writes[Status] = StatusWrites
  implicit val constraintsReads: Format[Constraints] = Json.format[Constraints]
  implicit val envelopeReads: Format[Envelope] = Json.format[Envelope]

  val MAX_ITEMS_DEFAULT = 1

  def fromJson(json: JsValue, _id: String, maxTTL: Int): Envelope = {
    val rawData = json.asInstanceOf[JsObject] ++ Json.obj("_id" -> _id)
    val envelope = Json.fromJson[Envelope](rawData).get
    val maxExpiryDate: DateTime = DateTime.now().plusDays(maxTTL)

    val expiryDate = envelope.expiryDate.map(d => if (d.isBefore(maxExpiryDate)) d else maxExpiryDate)
    envelope.copy(expiryDate = expiryDate)
  }

  def from(createEnvelope: Option[CreateEnvelope]): Envelope =
    createEnvelope.map(fromCreateEnvelope).getOrElse(emptyEnvelope())

  def emptyEnvelope(id : String = UUID.randomUUID().toString): Envelope =
    new Envelope(id, constraints = Some(emptyConstraints()))

  def emptyConstraints() =
    new Constraints(maxItems = Some(1))

  def fromCreateEnvelope(dto: CreateEnvelope) =
    emptyEnvelope().copy(constraints = dto.constraints.map(fromCreateConstraints), callbackUrl = dto.callbackUrl, expiryDate = dto.expiryDate, metadata = dto.metadata, files = None)

  private def fromCreateConstraints(dto: CreateConstraints): Constraints = {
    val maxItems: Int = dto.maxItems.getOrElse[Int]( MAX_ITEMS_DEFAULT )
    emptyConstraints ().copy(dto.contentTypes, Some(maxItems), dto.maxSize, dto.maxSizePerItem)
  }

}

object FileMetadata{
	implicit val fileMetaDataReads: Format[FileMetadata] = Json.format[FileMetadata]
}

case class FileMetadata(_id: String = UUID.randomUUID().toString,
                        filename: Option[String] = None,
                        contentType: Option[String] = None,
                        revision: Option[Int] = None,
                        metadata: Option[JsObject] = None)