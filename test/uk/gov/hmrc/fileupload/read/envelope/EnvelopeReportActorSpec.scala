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
import org.joda.time.{DateTime, DateTimeZone}
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

      sendEvent()

      modifiedEnvelope shouldBe initialState.copy(version = newVersion, callbackUrl = Some("callback-url"))
    }
    "mark file as quarantined" in new UpdateEnvelopeFixture {
      val event = FileQuarantined(envelopeId, FileId(), FileRefId(), 1, "name", "contentType", Json.obj("abc" -> "xyz"))

      sendEvent()

      val expectedEnvelope = initialState.copy(version = newVersion,
        files = Some(List(File(event.fileId, fileRefId = event.fileRefId,
          status = FileStatusQuarantined, name = Some(event.name), contentType = Some(event.contentType),
          length = None, uploadDate = Some(new DateTime(event.created, DateTimeZone.UTC)), revision = None, metadata = Some(event.metadata)))))

      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if virus was detected" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = VirusDetected(envelopeId, file.fileId, fileRefId = FileRefId())

      sendEvent()

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusError))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if file was clean" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = NoVirusDetected(envelopeId, file.fileId, fileRefId = FileRefId())

      sendEvent()

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusCleaned))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if file was copied from quarantine to transient" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = FileStored(envelopeId, file.fileId, fileRefId = FileRefId(), length = 1)

      sendEvent()

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusAvailable))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "delete a file" in new UpdateEnvelopeFixture {
      val otherFile = file.copy(fileId = FileId())
      override val initialState = Envelope(envelopeId, files = Some(Seq(file, otherFile)))
      val event = FileDeleted(envelopeId, file.fileId)

      sendEvent()

      val expectedEnvelope = initialState.copy(files = Some(List(otherFile)), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    // todo EnvelopeSealed needs 'application' as well
    "seal envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeSealed(initialState._id, "testRoutingReqId", "testDestination", "testApplication")

      sendEvent()

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, destination = Some(event.destination))
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "route envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeRouted(initialState._id)

      sendEvent()

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, status = EnvelopeStatusClosed)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "delete envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeDeleted(initialState._id)

      sendEvent()

      assert(deleteFunctionWasCalled)
    }
    "archive envelope (soft delete for transfer)" in new UpdateEnvelopeFixture {
      val event = EnvelopeArchived(envelopeId)

      sendEvent()

      val expectedEnvelope = initialState.copy(version = wrappedEvent.version, status = EnvelopeStatusDeleted)
      modifiedEnvelope shouldBe expectedEnvelope
    }
  }

  trait UpdateEnvelopeFixture {
    val envelopeId = EnvelopeId()
    var modifiedEnvelope: Envelope = _
    def save(e: Envelope) = {
      modifiedEnvelope = e
      Future.successful(true)
    }
    var deleteFunctionWasCalled: Boolean = false
    def delete(id: EnvelopeId) = {
      deleteFunctionWasCalled = true
      Future.successful(true)
    }

    val file = File(FileId(), fileRefId = FileRefId(),
      status = FileStatusQuarantined, name = Some("name"), contentType = Some("contentType"),
      length = None, uploadDate = Some(new DateTime(DateTimeZone.UTC)), revision = None, metadata = None)

    val initialState = Envelope(envelopeId)
    val newVersion = Version(999)

    def actor = TestActorRef(EnvelopeReportActor.props(get = _ => Future.successful(None),
      save = save, delete, defaultState = _ => initialState)(envelopeId))

    def event: EnvelopeEvent
    def wrappedEvent = Event(EventId("randomId"), streamId = StreamId("randomId"), version = newVersion, created = Created(1),
      eventType = EventType("eventType"), eventData = event)

    def sendEvent() = actor ! wrappedEvent
  }

}
