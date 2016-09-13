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

package uk.gov.hmrc.fileupload.read.envelope

import akka.actor.{ActorLogging, Props}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.domain.EventData
import uk.gov.hmrc.fileupload.read.infrastructure.ReportActor
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCreated, FileQuarantined, NoVirusDetected, VirusDetected}

import scala.concurrent.Future

class EnvelopeReportActor(override val id: EnvelopeId,
                          override val get: (EnvelopeId) => Future[Option[Envelope]],
                          override val save: (Envelope) => Future[Boolean],
                          override val defaultState: (EnvelopeId) => Envelope) extends ReportActor[Envelope] with ActorLogging {

  override def apply = {
    case (s: Envelope, e: EnvelopeCreated) =>
      s.copy(version = eventVersion)
    case (s: Envelope, e: FileQuarantined) =>
      val file = File(fileId = e.fileId, fileRefId = e.fileRefId, status = FileStatusQuarantined, name = Some(e.name), contentType = Some(e.contentType), metadata = Some(e.metadata))
      s.copy(version = eventVersion, files = s.files.orElse(Some(List.empty[File])).map(_.:+(file)))
    case (s: Envelope, e: NoVirusDetected) =>
      s.copy(version = eventVersion, files = s.files.map(f => f.map(f => if (f.fileRefId == e.fileRefId) f.copy(status = FileStatusCleaned) else f)))
    case (s: Envelope, e: VirusDetected) => s.copy(version = eventVersion)
  }

}

object EnvelopeReportActor {

  def props(get: (EnvelopeId) => Future[Option[Envelope]], save: (Envelope) => Future[Boolean], defaultState: (EnvelopeId) => Envelope)
           (id: EnvelopeId) =
    Props(new EnvelopeReportActor(id = id, get = get, save = save, defaultState = defaultState))
}
