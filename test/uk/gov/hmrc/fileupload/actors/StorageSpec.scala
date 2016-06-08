package uk.gov.hmrc.fileupload.actors

import akka.testkit.TestActors
import org.joda.time.DateTime
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import play.api.libs.json.{JsResult, JsObject, Json}
import reactivemongo.api.commands.DefaultWriteResult
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support

import uk.gov.hmrc.fileupload.models.{Envelope, Constraints}
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by Josiah on 6/4/2016.
  */
class StorageSpec extends ActorSpec with MockitoSugar {

  import scala.language.postfixOps
  import Support._
  import Storage._
  val storage = system.actorOf(Storage.props(envelopRepositoryStub))

  "A storage" should{
    "Respond with an Envelop when it receives a find by id message" in {
      within(500 millis){
        val optEnvelope = Some(Support.envelope)
        val envelope = optEnvelope.get
        envelopRepositoryStub.data = envelopRepositoryStub.data ++ Map(envelope._id -> envelope)
        storage ! FindById(envelope._id)
        expectMsg(optEnvelope)
      }
    }

    "Respond with nothing when it receives a find by id for a non existent id" in {
      val id = BSONObjectID.generate
      storage ! FindById(id)
      expectMsg(Option.empty[Envelope])
    }

	  "respond with an Id when it receives a create envelope message" in {
		  within(500 millis) {

			  val id = BSONObjectID.generate
			  val rawData = Support.envelopeBody.asInstanceOf[JsObject] ++ Json.obj("_id" -> id.stringify)
			  val envelope = Json.fromJson[Envelope](rawData).get

			  storage ! Save(envelope)

			  expectMsg(id)
		  }
	  }

	  "respond with an success true after deleting an Envelope" in {
		  within(500 millis) {

			  val envelopeRepositoryMock = mock[EnvelopeRepository]
			  val storage = system.actorOf(Storage.props(envelopeRepositoryMock))

			  when(envelopeRepositoryMock.delete(Matchers.any())(Matchers.any())).thenReturn(Future.successful(true))
			  storage ! Remove(envelope._id )

			  expectMsg(true)
		  }
	  }

	  "respond with an success false when not able to delete an Envelope" in {
		  within(500 millis) {

			  val envelopeRepositoryMock = mock[EnvelopeRepository]
			  val storage = system.actorOf(Storage.props(envelopeRepositoryMock))

			  when(envelopeRepositoryMock.delete(Matchers.any())(Matchers.any())).thenReturn(Future.successful(false))
			  storage ! Remove(envelope._id )

			  expectMsg(false)
		  }
	  }
  }


}
