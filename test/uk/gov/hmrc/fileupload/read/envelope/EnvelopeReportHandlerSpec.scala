/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.Support.fileRefId
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.Future

class EnvelopeReportHandlerSpec extends AnyWordSpecLike with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global

  val envelopeConstraints = Some(EnvelopeFilesConstraints(maxItems = 10,
    maxSize = Size("100MB").right.get,
    maxSizePerItem = Size("10MB").right.get,
    allowZeroLengthFiles = None))

  "EnvelopeReportActor" should {
    "create a new envelope" in new UpdateEnvelopeFixture {
      val callbackUrl = Some("callback-url")
      val expiryDate = Some(new DateTime())
      val metadata = Some(Json.obj("key" -> "value"))
      val event = EnvelopeCreated(envelopeId, callbackUrl, expiryDate, metadata, envelopeConstraints)

      sendEvent(event)

      modifiedEnvelope shouldBe initialState.copy(version = newVersion,
        callbackUrl = callbackUrl,
        expiryDate = expiryDate,
        metadata = metadata,
        constraints = envelopeConstraints)
    }
    "mark file as quarantined" in new UpdateEnvelopeFixture {
      val event = FileQuarantined(envelopeId, FileId(), fileRefId(), 1, "name", "contentType", Some(123L), Json.obj("abc" -> "xyz"))

      sendEvent(event)

      val expectedEnvelope = initialState.copy(version = newVersion,
        files = Some(List(File(event.fileId, fileRefId = event.fileRefId,
          status = FileStatusQuarantined, name = Some(event.name), contentType = Some(event.contentType),
          length = Some(123L), uploadDate = Some(new DateTime(event.created, DateTimeZone.UTC)), revision = None, metadata = Some(event.metadata)))))

      modifiedEnvelope shouldBe expectedEnvelope
    }
    "create a new envelope and mark file as quarantined" in new UpdateEnvelopeFixture {
      val callbackUrl = Some("callback-url")
      val expiryDate = Some(new DateTime())
      val metadata = Some(Json.obj("key" -> "value"))
      val envelopeCreated = EnvelopeCreated(envelopeId, callbackUrl, expiryDate, metadata, envelopeConstraints)
      val fileQuarantined = FileQuarantined(envelopeId, FileId(), fileRefId(), 1, "name", "contentType", Some(123L), Json.obj("abc" -> "xyz"))

      val events = List(envelopeCreated, fileQuarantined)

      sendEvents(events)

      val expectedEnvelope = initialState.copy(version = Version(2),
        callbackUrl = callbackUrl,
        expiryDate = expiryDate,
        metadata = metadata,
        constraints = envelopeConstraints,
        files = Some(List(File(fileQuarantined.fileId, fileRefId = fileQuarantined.fileRefId,
          status = FileStatusQuarantined, name = Some(fileQuarantined.name), contentType = Some(fileQuarantined.contentType),
          length = Some(123L), uploadDate = Some(new DateTime(fileQuarantined.created, DateTimeZone.UTC)), revision = None, metadata = Some(fileQuarantined.metadata)))))

      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if virus was detected" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = VirusDetected(envelopeId, file.fileId, fileRefId = fileRefId())

      sendEvent(event)

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusInfected))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if file was clean" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = NoVirusDetected(envelopeId, file.fileId, fileRefId = fileRefId())

      sendEvent(event)

      val expectedEnvelope = initialState.copy(files = Some(Seq(file.copy(status = FileStatusCleaned))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "update file status if file was copied from quarantine to transient" in new UpdateEnvelopeFixture {
      override val initialState = Envelope(files = Some(List(file)))
      val event = FileStored(envelopeId, file.fileId, fileRefId = fileRefId(), length = 1)

      sendEvent(event)

      val expectedEnvelope = initialState.copy(files = Some(
        Seq(file.copy(status = FileStatusAvailable, length = Some(event.length)))), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "delete a file" in new UpdateEnvelopeFixture {
      val otherFile = file.copy(fileId = FileId())
      override val initialState = Envelope(envelopeId, files = Some(Seq(file, otherFile)))
      val event = FileDeleted(envelopeId, file.fileId)

      sendEvent(event)

      val expectedEnvelope = initialState.copy(files = Some(List(otherFile)), version = newVersion)
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "seal envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeSealed(initialState._id, "testRoutingReqId", "testDestination", "testApplication")

      sendEvent(event)

      val expectedEnvelope = initialState.copy(
        version = Version(1), status = EnvelopeStatusSealed, destination = Some(event.destination), application = Some(event.application)
      )
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "unseal envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeSealed(initialState._id, "testRoutingReqId", "testDestination", "testApplication")
      val event1 = EnvelopeUnsealed(initialState._id)

      sendEvent(event)
      sendEvent(event1)

      val expectedEnvelope = initialState.copy(
        version = Version(1), status = EnvelopeStatusOpen, destination = None, application = None
      )
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "route envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeRouted(initialState._id, isPushed = false)

      sendEvent(event)

      val expectedEnvelope = initialState.copy(
        version = Version(1),
        status = EnvelopeStatusClosed,
        isPushed = Some(event.isPushed))
      modifiedEnvelope shouldBe expectedEnvelope
    }
    "delete envelope" in new UpdateEnvelopeFixture {
      val event = EnvelopeDeleted(initialState._id)

      sendEvent(event)

      assert(deleteFunctionWasCalled)
    }
    "archive envelope (soft delete for transfer)" in new UpdateEnvelopeFixture {
      val event = EnvelopeArchived(envelopeId)

      sendEvent(event)

      val expectedEnvelope = initialState.copy(version = Version(1), status = EnvelopeStatusDeleted)
      modifiedEnvelope shouldBe expectedEnvelope
    }
  }

  trait UpdateEnvelopeFixture {
    val envelopeId = EnvelopeId()
    var modifiedEnvelope: Envelope = _
    def update(e: Envelope, replay: Boolean = false) = {
      modifiedEnvelope = e
      Future.successful(Repository.updateSuccess)
    }
    var deleteFunctionWasCalled: Boolean = false
    def delete(id: EnvelopeId) = {
      deleteFunctionWasCalled = true
      Future.successful(Repository.deleteSuccess)
    }

    val file = File(FileId(), fileRefId = fileRefId(),
      status = FileStatusQuarantined, name = Some("name"), contentType = Some("contentType"),
      length = None, uploadDate = Some(new DateTime(DateTimeZone.UTC)), revision = None, metadata = None)

    val initialState = Envelope(envelopeId)
    val newVersion = Version(1)

    def handler = new EnvelopeReportHandler(
      (streamId: StreamId) => EnvelopeId(streamId.value),
      update = update,
      delete = delete,
      defaultState = _ => initialState)

    def wrappedEvent(e: EnvelopeEvent, version: Version = newVersion) = Event(EventId("randomId"), streamId = StreamId("randomId"),
      version = version, created = Created(1), eventType = EventType("eventType"), eventData = e)

    def wrappedEvents(events: Seq[EnvelopeEvent]) = events.zipWithIndex.map(i => wrappedEvent(i._1, Version(i._2 + 1)))

    def sendEvent(event: EnvelopeEvent): Unit = handler.handle(replay = false)(List(wrappedEvent(event)))

    def sendEvents(events: Seq[EnvelopeEvent]): Unit = handler.handle(replay = false)(wrappedEvents(events))
  }
}
