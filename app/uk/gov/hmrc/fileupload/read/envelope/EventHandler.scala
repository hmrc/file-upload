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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import uk.gov.hmrc.fileupload.write.envelope._

class EventHandler(subscribe: (ActorRef, Class[_]) => Boolean, repository: Repository) extends Actor with ActorLogging {

  //TODO: remove that
  import scala.concurrent.ExecutionContext.Implicits.global

  override def preStart = {
    subscribe(self, classOf[EnvelopeCreated])
    subscribe(self, classOf[FileQuarantined])
    subscribe(self, classOf[NoVirusDetected])
    subscribe(self, classOf[FileStored])
    subscribe(self, classOf[VirusDetected])
    subscribe(self, classOf[EnvelopeSealed])
  }

  override def receive = {
    case e: EnvelopeCreated =>
      val envelope = Envelope(_id = e.id)
      repository.add(envelope)

    case e: FileQuarantined =>
      val file = File(fileId = e.fileId, fileReferenceId = e.fileReferenceId, status = FileStatusQuarantined, name = Some(e.name), contentType = Some(e.contentType), metadata = Some(e.metadata))
      repository.upsertFileMetadata(e.id, file)

    case e: NoVirusDetected =>
      repository.updateFileStatus(e.id, e.fileId, FileStatusCleaned)

    case e: FileStored =>
      log.info(s"$e not yet implemented")
    case e: VirusDetected =>
      log.info(s"$e not yet implemented")
    case e: EnvelopeSealed =>
      log.info(s"$e not yet implemented")
    case a: AnyRef => {
      println(a)
    }
  }

}

object EventHandler {

  def props(subscribe: (ActorRef, Class[_]) => Boolean, repository: Repository) =
    Props(new EventHandler(subscribe = subscribe, repository = repository))
}
