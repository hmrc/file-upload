package uk.gov.hmrc.fileupload.actors

import akka.actor.{Inbox, Actor, ActorSystem}
import akka.testkit.{TestActorRef, TestActors}
import org.joda.time.DateTime
import play.api.libs.json.{Json, JsObject, JsValue}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.EnvelopeStorage.Persist
import uk.gov.hmrc.fileupload.actors.IdGenerator.NextId
import uk.gov.hmrc.fileupload.models.Envelope
import scala.concurrent.Future
import scala.concurrent.duration._
/**
  * Created by Josiah on 6/3/2016.
  */
class EnvelopeManagerSpec extends ActorSpec{

  import scala.language.postfixOps
  import EnvelopeManager._
  val storage = system.actorOf(TestActors.echoActorProps)
	val IdGenerator = TestActorRef[ActorStub]
  val envelopMgr = system.actorOf(EnvelopeManager.props(storage, IdGenerator))
	implicit val ec = system.dispatcher

  "An EnvelopeManager" should  {
    "respond with BSONObjectID when it receives a GetEnvelop message" in {
      within(500 millis){
        val id = "5752051b69ff59a8732f6474"
        envelopMgr ! GetEnvelope(id)
        expectMsg(EnvelopeStorage.FindById(BSONObjectID(id)))
      }
    }
  }

	"An EnvelopeManager" should {
		"respond with an Envelope when it receives a CreateEnvelope message" in {
			within(500 millis) {
				val rawData = Support.envelopeBody
				val id = BSONObjectID.generate

				IdGenerator.underlyingActor.setReply(id)

				envelopMgr ! CreateEnvelope( Support.envelopeBody )

				val expectedJsonEnvelope = Support.envelopeBody.asInstanceOf[JsObject] ++  Json.obj("_id" -> id.stringify)

				val envelope = Json.fromJson[Envelope](expectedJsonEnvelope).get
				expectMsg(Persist(envelope))
			}
		}
	}

	"expiryDate" should {
		"be overridden when it is greater than the max expiry days configured" in {
			val inbox: Inbox = Inbox.create(system)
			val envelopMgr = system.actorOf(EnvelopeManager.props(inbox.getRef(), IdGenerator))

			within(500 millis) {
				import Envelope._

				val now: DateTime = DateTime.now()
				val expiryDate: DateTime = now.plusDays(3)
				val maxExpiryDate: DateTime = now.plusDays(2)

				val id = BSONObjectID.generate
				IdGenerator.underlyingActor.setReply(id)

				println(Json.toJson(Support.envelope))

				envelopMgr ! CreateEnvelope( Json.toJson(Support.envelope) )

				val Persist(envelope) =  inbox.receive(timeout.duration).asInstanceOf[Persist]
				envelope.expiryDate.isBefore(maxExpiryDate) shouldBe true

			}
		}
	}


}
