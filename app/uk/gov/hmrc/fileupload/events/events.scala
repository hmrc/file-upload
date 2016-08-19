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

package uk.gov.hmrc.fileupload.events

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

sealed trait Event

case class Quarantined(envelopeId: EnvelopeId, fileId: FileId) extends Event

case class ToTransientMoved(envelopeId: EnvelopeId, fileId: FileId) extends Event

case class MovingToTransientFailed(envelopeId: EnvelopeId, fileId: FileId, reason: String) extends Event

case class NoVirusDetected(envelopeId: EnvelopeId, fileId: FileId) extends Event

case class VirusDetected(envelopeId: EnvelopeId, fileId: FileId, reason: String) extends Event


object EventFormatters {
  implicit val noVirusDetectedFormat: Format[NoVirusDetected] = Json.format[NoVirusDetected]
  implicit val quarantinedFormat: Format[Quarantined] = Json.format[Quarantined]
  implicit val toTransientMovedFormat: Format[ToTransientMoved] = Json.format[ToTransientMoved]
  implicit val movingToTransientFailedFormat: Format[MovingToTransientFailed] = Json.format[MovingToTransientFailed]
  implicit val virusDetectedFormat: Format[VirusDetected] = Json.format[VirusDetected]
}