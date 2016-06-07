package uk.gov.hmrc.fileupload.actors

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.TestActorRef
import play.api.Mode
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

trait Actors{

	def actorSystem: ActorSystem

	def envelopeMgr: ActorRef

	def envelopeStorage: ActorRef

	def idGenerator: ActorRef
}

object FileUploadActors extends Actors{

	override lazy val actorSystem: ActorSystem = ActorSystem("file-upload-actor-system")

	override lazy val envelopeStorage: ActorRef = actorSystem.actorOf(Storage.props(EnvelopeRepository(EnvelopeRepository.db)), "envelope-storage")

	override lazy val idGenerator: ActorRef = actorSystem.actorOf(IdGenerator.props, "id-generator")

	override lazy val envelopeMgr: ActorRef = actorSystem.actorOf(EnvelopeService.props(envelopeStorage, idGenerator, play.api.Play.current.configuration.getInt("envelope.maxTTL").get), "envelope-mgr")
}

object FileUploadTestActors extends Actors{

	import scala.language.implicitConversions

	override implicit val actorSystem: ActorSystem = ActorSystem("test-actor-system")

	override val envelopeMgr: ActorRef = TestActorRef[ActorStub]

	override val envelopeStorage: ActorRef = TestActorRef[ActorStub]

	override val idGenerator: ActorRef = TestActorRef[ActorStub]

	implicit def underLyingActor[T <: Actor](actorRef: ActorRef): T = actorRef.asInstanceOf[TestActorRef[T]].underlyingActor
}

object Actors extends Actors{

	val mode = play.api.Play.current.mode

	override val actorSystem: ActorSystem = if(mode == Mode.Test) FileUploadTestActors.actorSystem else FileUploadActors.actorSystem

	override val envelopeMgr: ActorRef = if(mode == Mode.Test) FileUploadTestActors.envelopeMgr else FileUploadActors.envelopeMgr

	override val envelopeStorage: ActorRef = if(mode == Mode.Test) FileUploadTestActors.envelopeStorage else FileUploadActors.envelopeStorage

	override val idGenerator: ActorRef = if(mode == Mode.Test) FileUploadTestActors.idGenerator else FileUploadActors.idGenerator
}

class ActorStub extends Actor{
	var _reply: Option[Any] = None

	def setReply(reply: Any): Unit = _reply = Some(reply)

	override def receive = {
		case _ => sender() ! _reply.getOrElse( throw new RuntimeException("No reply set"))
	}
}
