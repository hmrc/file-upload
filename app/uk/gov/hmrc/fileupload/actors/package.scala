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

package uk.gov.hmrc.fileupload

import akka.actor._
import akka.testkit.TestActorRef
import play.api.Mode
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

/**
  * Created by Josiah on 6/5/2016.
  */
package object actors {

  trait Actors{

    def actorSystem: ActorSystem

    def envelopeMgr: ActorRef

    def envelopeStorage: ActorRef

	  def idGenerator: ActorRef
  }

  object FileUploadActors extends Actors{

    override lazy val actorSystem: ActorSystem = ActorSystem("file-upload-actor-system")

    override lazy val envelopeStorage: ActorRef = actorSystem.actorOf(EnvelopeStorage.props(EnvelopeRepository(EnvelopeRepository.db)), "envelope-storage")

	  override lazy val idGenerator: ActorRef = actorSystem.actorOf(IdGenerator.props, "id-generator")

    override lazy val envelopeMgr: ActorRef = actorSystem.actorOf(EnvelopeManager.props(envelopeStorage, idGenerator, play.api.Play.current.configuration.getInt("envelope.maxTTL").get), "envelope-mgr")
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
}
