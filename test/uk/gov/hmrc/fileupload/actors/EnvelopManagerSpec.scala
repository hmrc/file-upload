package uk.gov.hmrc.fileupload.actors

import akka.actor.ActorSystem
import akka.testkit.TestActors
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration._
/**
  * Created by Josiah on 6/3/2016.
  */
class EnvelopManagerSpec extends ActorSpec{

  import scala.language.postfixOps
  import EnvelopeManager._
  val storage = system.actorOf(TestActors.echoActorProps)
  val envelopMgr = system.actorOf(EnvelopeManager.props(storage))

  "An EnvelopManager" should  {
    "respond with BSONObjectID when it receives a GetEnvelop message" in {
      within(500 millis){
        val id = "5752051b69ff59a8732f6474"
        envelopMgr ! GetEnvelope(id)
        expectMsg(EnvelopeStorage.FindById(BSONObjectID(id)))
      }
    }
  }
}
