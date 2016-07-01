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

package uk.gov.hmrc.fileupload.actors

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.TestActorRef
import play.api.Mode
import uk.gov.hmrc.fileupload.repositories.{DefaultMongoConnection, EnvelopeRepository}

trait Actors {

	def actorSystem: ActorSystem

	def envelopeService: ActorRef

	def storage: ActorRef

	def fileUploader(envelopeId: String, fileId: String): ActorRef
}

object FileUploadActors extends Actors {

	val envelopeRepository = EnvelopeRepository(DefaultMongoConnection.db)

	override lazy val actorSystem: ActorSystem = ActorSystem("file-upload-actor-system")

	override lazy val storage: ActorRef = actorSystem.actorOf(Storage.props(envelopeRepository), "storage")

	override lazy val envelopeService: ActorRef = actorSystem.actorOf(EnvelopeService.props(storage, Actors.maxTTL), "envelope-service")

	override def fileUploader(envelopeId: String, fileId: String): ActorRef = actorSystem.actorOf(FileUploader.props(envelopeId, fileId, FileUploadActors.envelopeRepository))
}

object FileUploadTestActors extends Actors {

	import scala.language.implicitConversions

	override implicit val actorSystem: ActorSystem = ActorSystem("test-actor-system")

	override val envelopeService: ActorRef = {
		val actor = TestActorRef[ActorStub]
		actor.underlyingActor.name = Some("envelopeService")
		actor
	}

	override val storage: ActorRef = {
		val actor = TestActorRef[ActorStub]
		actor.underlyingActor.name = Some("storage")
		actor
	}

	val fileUploader = {
		val actor = TestActorRef[ActorStub]
		actor.underlyingActor.name = Some("marshaller")
		actor
	}

	override def fileUploader(envelopeId: String, fileId: String): ActorRef = fileUploader
}

object Actors extends Actors{

	val maxTTL = play.api.Play.current.configuration.getInt("envelope.maxTTL").get

	val mode = play.api.Play.current.mode

	override val actorSystem: ActorSystem = if(mode == Mode.Test) FileUploadTestActors.actorSystem else FileUploadActors.actorSystem

	override val envelopeService: ActorRef = if(mode == Mode.Test) FileUploadTestActors.envelopeService else FileUploadActors.envelopeService

	override val storage: ActorRef = if(mode == Mode.Test) FileUploadTestActors.storage else FileUploadActors.storage

	override def fileUploader(envelopeId: String, fileId: String): ActorRef =
		if(mode == Mode.Test) FileUploadTestActors.fileUploader else FileUploadActors.fileUploader(envelopeId, fileId)
}

class ActorStub extends Actor{

	var name: Option[String] = None

	def setReceive(me: ( => ActorRef) =>  Receive ) = context.become(me(sender))

	def setReply(reply: Any)  = setReceive((sender) => { case _ => sender ! reply})

	override def receive = {
		case _ => throw new RuntimeException(s"No receive set for ${name.getOrElse("UNNAMED")}")
	}

}
