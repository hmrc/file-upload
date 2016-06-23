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

import java.util.{NoSuchElementException, UUID}

import akka.testkit.TestActorRef
import akka.util.Timeout
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeNotFoundException, FileMetadata}

import scala.concurrent.duration._
import scala.util.{Failure, Try}

/**
  * Created by Josiah on 6/3/2016.
  */
class EnvelopeServiceSpec extends ActorSpec{

  import EnvelopeService._

  import scala.language.postfixOps
  val MAX_TIME_TO_LIVE = 2

	val storage = TestActorRef[ActorStub]
	val IdGenerator = TestActorRef[ActorStub]
	val marshaller = TestActorRef[ActorStub]
  val envelopService = system.actorOf(EnvelopeService.props(storage, IdGenerator, marshaller, MAX_TIME_TO_LIVE))
	implicit val ec = system.dispatcher


	"An EnvelopeService" should  {
		"respond with an envelope when it receives a GetEnvelop message" in {
			within(timeout){
	      val envelope = Support.envelope
	      val json = Json.toJson[Envelope](envelope)
	      val id = envelope._id
				println(json)

	      storage.underlyingActor.setReply(Some(envelope))

	      envelopService ! GetEnvelope(id)
	      expectMsg(envelope)
      }
    }
		"respond with a BadRequestException when it receives a GetEnvelope message with an invalid id" in {
			within(timeout){
	      val id = UUID.randomUUID().toString

	      storage.underlyingActor.setReply(None)

	      envelopService ! GetEnvelope(id)
	      expectMsg(new EnvelopeNotFoundException(id))
      }
    }
  }


	"An EnvelopeService" should {
		"respond with id  of created envelope when it receives a CreateEnvelope message" in {
			within(timeout) {
				val rawData = Support.envelopeBody
				val id = UUID.randomUUID().toString

				IdGenerator.underlyingActor.setReply(id)
				storage.underlyingActor.setReply(id)
        marshaller.underlyingActor.setReply(Try(Support.envelope))

				envelopService ! CreateEnvelope( Some(rawData) )

				expectMsg(id)
			}
		}

		"respond with an exception when creation fails" in {
      within(timeout) {
        val wrongData = Json.parse( """{"wrong": "json"}""" )
        val id: String = UUID.randomUUID().toString
        IdGenerator.underlyingActor.setReply(id)
        storage.underlyingActor.setReply(id)
        marshaller.underlyingActor.setReply(Failure(new NoSuchElementException("JsError.get")))

        envelopService ! CreateEnvelope(Some(wrongData))

        expectMsgClass(classOf[NoSuchElementException])
      }
    }

		"respond with Success after deleting an envelope" in {
			within(timeout){
				val id = "5752051b69ff59a8732f6474"
				storage.underlyingActor.setReply(true)
				envelopService ! DeleteEnvelope(id)
				expectMsg(true)
			}
		}

		"update an envelope to add a new file" in {
			within(timeout){
				storage.underlyingActor.setReply(true)
				envelopService ! UpdateEnvelope(envelopeId = "123", fileId = "456")
				expectMsg(true)
			}
		}
		"add metadata to file when envelope exist" in {
			within(timeout){
				storage.underlyingActor.setReply(true)
				val data = FileMetadata(_id = nextId())
				envelopService ! UpdateFileMetaData(data)
				expectMsg(true)
			}
		}
	}


	import scala.language.implicitConversions
	implicit def timeoutToDuration(timeout: Timeout): FiniteDuration = timeout.duration
}
