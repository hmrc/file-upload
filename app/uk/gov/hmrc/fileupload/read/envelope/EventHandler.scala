package uk.gov.hmrc.fileupload.read.envelope

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import uk.gov.hmrc.fileupload.write.envelope._

class EventHandler(subscribe: (ActorRef, Class[_]) => Boolean, repository: Repository) extends Actor with ActorLogging {

  //TODO: remove that
  import scala.concurrent.ExecutionContext.Implicits.global

  override def preStart = {
    subscribe(self, classOf[EnvelopeCreated])
    subscribe(self, classOf[FileQuarantined])
    subscribe(self, classOf[FileCleaned])
    subscribe(self, classOf[FileStored])
    subscribe(self, classOf[FileInfected])
    subscribe(self, classOf[EnvelopeSealed])
  }

  override def receive = {
    case e: EnvelopeCreated =>
      val envelope = Envelope(_id = e.id)
      repository.add(envelope)

    case e: FileQuarantined =>
      val file = File(fileId = e.fileId, fileReferenceId = e.fileReferenceId, status = FileStatusQuarantined, name = Some(e.name), contentType = Some(e.contentType), metadata = Some(e.metadata))
      repository.upsertFileMetadata(e.id, file)

    case e: FileCleaned =>
      log.info(s"$e not yet implemented")
    case e: FileStored =>
      log.info(s"$e not yet implemented")
    case e: FileInfected =>
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
