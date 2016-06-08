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

import java.lang.Math.abs

import akka.actor.{ActorRef, Inbox, Actor, ActorSystem}
import akka.testkit.{TestActorRef, TestActors}
import org.joda.time.DateTime
import org.junit.Assert
import org.junit.Assert.assertTrue
import play.api.libs.json.{Json, JsObject, JsValue}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.Storage.Save
import uk.gov.hmrc.fileupload.actors.IdGenerator.NextId
import uk.gov.hmrc.fileupload.models.{ValidationException, Envelope}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
  * Created by Josiah on 6/3/2016.
  */
class EnvelopeManagerSpec extends ActorSpec{

  import scala.language.postfixOps
  import EnvelopeService._
  val MAX_TIME_TO_LIVE = 2

	val storage = system.actorOf(TestActors.echoActorProps)
	val IdGenerator = TestActorRef[ActorStub]
  val envelopMgr = system.actorOf(EnvelopeService.props(storage, IdGenerator, MAX_TIME_TO_LIVE))
	implicit val ec = system.dispatcher

  "An EnvelopeManager" should  {
    "respond with BSONObjectID when it receives a GetEnvelop message" in {
      within(timeout.duration){
        val id = "5752051b69ff59a8732f6474"
        envelopMgr ! GetEnvelope(id)
        expectMsg(Storage.FindById(BSONObjectID(id)))
      }
    }
  }

	"An EnvelopeManager" should {
		"respond with an Envelope when it receives a CreateEnvelope message" in {
			within(timeout.duration) {
				val rawData = Support.envelopeBody
				val id = BSONObjectID.generate

				IdGenerator.underlyingActor.setReply(id)

				envelopMgr ! CreateEnvelope( rawData )

				val expectedJsonEnvelope = rawData.asInstanceOf[JsObject] ++  Json.obj("_id" -> id.stringify)

				val envelope = Json.fromJson[Envelope](expectedJsonEnvelope).get
				expectMsg(Save(envelope))
			}
		}
	}

	"expiryDate" should {
		"be overridden when it is greater than the max expiry days configured" in {
			val inbox: Inbox = Inbox.create(system)
			val storage: ActorRef = inbox.getRef()
			val envelopMgr = system.actorOf(EnvelopeService.props(storage, IdGenerator, MAX_TIME_TO_LIVE))

			within(timeout.duration) {
				import Envelope._

				val now: DateTime = DateTime.now()
				val maxExpiryDate: DateTime = now.plusDays(2)

				val id = BSONObjectID.generate
				IdGenerator.underlyingActor.setReply(id)

				envelopMgr ! CreateEnvelope( Json.toJson(Support.farInTheFutureEnvelope) )

				val Save(envelope) =  inbox.receive(timeout.duration).asInstanceOf[Save]
				assertTrue( isWithinAMinute(maxExpiryDate, envelope.expiryDate) )

			}
		}
	}

	"An EnvelopeManager" should  {
		"respond with Success after deleting an envelope" in {
			within(timeout.duration){
				val id = "5752051b69ff59a8732f6474"
				envelopMgr ! DeleteEnvelope(id)
				expectMsg( Storage.Remove(BSONObjectID(id)) )
			}
		}
	}

	def isWithinAMinute(maxExpiryDate: DateTime, exipiryDate: DateTime): Boolean = {
		abs(exipiryDate.getMillis - maxExpiryDate.getMillis) < 60 * 1000
	}
}
