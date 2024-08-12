/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.stats

import org.apache.pekko.actor.{Actor, ActorRef, Props}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.Event

import scala.concurrent.Future

class StatsActor(
  subscribe          : (ActorRef, Class[_]) => Boolean,
  save               : FileQuarantined      => Unit,
  deleteVirusDetected: VirusDetected        => Unit,
  deleteFileStored   : FileStored           => Unit,
  deleteFiles        : EnvelopeDeleted      => Unit
) extends Actor:

  override def preStart(): Unit =
    subscribe(self, classOf[Event])

  def receive = {
    case event: Event =>
      event.eventData match
        case e: FileQuarantined =>
          save(e)
        case e: VirusDetected =>
          deleteVirusDetected(e)
        case e: FileStored =>
          deleteFileStored(e)
        case e: EnvelopeDeleted =>
          deleteFiles(e)
        case _ =>
  }

end StatsActor

object StatsActor:
  def props(
    subscribe          : (ActorRef, Class[_]) => Boolean,
    findEnvelope       : EnvelopeId           => Future[FindResult],
    save               : FileQuarantined      => Unit,
    deleteVirusDetected: VirusDetected        => Unit,
    deleteFileStored   : FileStored           => Unit,
    deleteFiles        : EnvelopeDeleted      => Unit
  ) =
    Props(StatsActor(
      subscribe           = subscribe,
      save                = save,
      deleteFileStored    = deleteFileStored,
      deleteVirusDetected = deleteVirusDetected,
      deleteFiles         = deleteFiles
    ))
