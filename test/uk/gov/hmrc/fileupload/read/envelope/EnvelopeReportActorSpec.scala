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

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.joda.time.DateTime
import org.scalatest.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId, StopSystemAfterAll}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class EnvelopeReportActorSpec extends TestKit(ActorSystem("envelope-report")) with UnitSpec with Matchers with StopSystemAfterAll {

  "EnvelopeReportActor" should {
    "create a new envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeCreated(envelopeId, Some("callback-url"))

      actor ! wrappedEvent

      savedEnvelope shouldBe initialState.copy(version = newVersion, callbackUrl = Some("callback-url"))
    }
    "mark file as quarantined" in new UpdateEnvelopeFixture {
      // TODO FileQuarantined should contain date created
      val event = FileQuarantined(envelopeId, FileId(), fileRefId = FileRefId(), "name", "contentType",
        metadata = Json.obj("abc" -> "xyz"))

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(version = newVersion,
        files = Some(List(File(event.fileId, fileRefId = event.fileRefId,
          status = FileStatusQuarantined, name = Some(event.name), contentType = Some(event.contentType),
          length = None, revision = None, metadata = Some(event.metadata)))))

      savedEnvelope.toString shouldBe expectedEnvelope.toString
    }
    "update file status if virus was detected" in new UpdateEnvelopeFixture {
      val file = File(FileId(), fileRefId = FileRefId(),
        status = FileStatusQuarantined, name = Some("name"), contentType = Some("contentType"),
        length = None, uploadDate = Some(new DateTime()), revision = None, metadata = None)
      override val initialState = Envelope(files = Some(List(file)))
      val event = VirusDetected(envelopeId, file.fileId, fileRefId = FileRefId())

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusError))), version = newVersion)
      savedEnvelope shouldBe expectedEnvelope
    }
    "update file status if file was clean" in new UpdateEnvelopeFixture {
      val file = File(FileId(), fileRefId = FileRefId(),
        status = FileStatusQuarantined, name = Some("name"), contentType = Some("contentType"),
        length = None, uploadDate = Some(new DateTime()), revision = None, metadata = None)
      override val initialState = Envelope(files = Some(List(file)))
      val event = NoVirusDetected(envelopeId, file.fileId, fileRefId = FileRefId())

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusCleaned))), version = newVersion)
      savedEnvelope shouldBe expectedEnvelope
    }
    // todo EnvelopeSealed needs 'application' as well
    "seal envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeSealed(initialState._id, "destination", "packageZip")

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, destination = Some(event.destination))
      savedEnvelope shouldBe expectedEnvelope
    }
    "route envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeRouted(initialState._id)

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, status = EnvelopeStatusClosed)
      savedEnvelope shouldBe expectedEnvelope
    }
    "delete envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeDeleted(initialState._id)

      actor ! wrappedEvent

      assert(deleteFunctionWasCalled)
    }
    "archive envelope (soft delete for transfer)" in new UpdateEnvelopeFixture {
      val event = EnvelopeArchived(envelopeId)

      actor ! wrappedEvent

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, status = EnvelopeStatusDeleted)
      savedEnvelope shouldBe expectedEnvelope
    }
  }

  trait UpdateEnvelopeFixture {
    val envelopeId = EnvelopeId()
    var savedEnvelope: Envelope = _
    def save(e: Envelope) = {
      savedEnvelope = e
      Future.successful(true)
    }
    var deleteFunctionWasCalled: Boolean = false
    def delete(id: EnvelopeId) = {
      deleteFunctionWasCalled = true
      Future.successful(true)
    }

    val initialState = Envelope()
    val newVersion = Version(999)

    def actor = TestActorRef(EnvelopeReportActor.props(get = _ => Future.successful(None),
      save = save, delete, defaultState = _ => initialState)(envelopeId))

    def event: EnvelopeEvent
    def wrappedEvent = Event(EventId("randomId"), streamId = StreamId("randomId"), version = newVersion, created = Created(1),
      eventType = EventType("eventType"), eventData = event)
  }

}
