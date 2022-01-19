/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.write.envelope

import java.util.UUID

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, Size}
import uk.gov.hmrc.fileupload.write.infrastructure.EventData

import scala.collection.mutable.ListBuffer

class EnvelopeSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope] with TestApplicationComponents {

  override val handler = new EnvelopeHandler(envelopeConstraintsConfigure)

  override val defaultStatus: Envelope = Envelope()

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val envelopeFilesConstraints =
    EnvelopeFilesConstraints(maxItems = 10,
                             maxSize = Size("100MB").right.get,
                             maxSizePerItem = Size("10MB").right.get,
                             allowZeroLengthFiles = None)
  val envelopeCreated = EnvelopeCreated(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
    Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints))

  def envelopeCreatedWithLimitedMaxItemConstraint(constraints: EnvelopeFilesConstraints) = EnvelopeCreated(envelopeId,
                                                  Some("http://www.callback-url.com"),
                                                  Some(new DateTime(0)),
                                                  Some(Json.obj("foo" -> "bar")),
                                                  Some(constraints))
  val fileQuarantined = FileQuarantined(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj())
  val noVirusDetected = NoVirusDetected(envelopeId, fileId, fileRefId)
  val virusDetected = VirusDetected(envelopeId, fileId, fileRefId)
  val fileStored = FileStored(envelopeId, fileId, fileRefId, 100)
  val fileDeleted = FileDeleted(envelopeId, fileId)
  val envelopeDeleted = EnvelopeDeleted(envelopeId)
  val envelopeSealed = EnvelopeSealed(envelopeId, "testRoutingRequestId", "DMS", "testApplication")
  val envelopeUnsealed = EnvelopeUnsealed(envelopeId)
  val envelopeRouteRequested = EnvelopeRouteRequested(envelopeId)
  val envelopeRouted = EnvelopeRouted(envelopeId, isPushed = true)
  val envelopeArchived = EnvelopeArchived(envelopeId)

  def defaultFileRefId = FileRefId(UUID.randomUUID().toString)

  Feature("CreateEnvelope") {

    Scenario("Create new envelope") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints)),
        envelopeCreated
      )
    }

    Scenario("Create new envelope for an existing envelope") {
      givenWhenThen(
        envelopeCreated,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints)),
        EnvelopeAlreadyCreatedError
      )
    }

    Scenario("Create new envelope with number of items < 1") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints.copy(maxItems = 0))),
        InvalidMaxItemCountConstraintError
      )
    }

    Scenario("Create new envelope with out of bounds max size per item constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints.copy(maxSizePerItem = Size("101MB").right.get))),
        InvalidMaxSizePerItemConstraintError
      )
    }

    Scenario("Create new envelope with out of bounds max size constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints.copy(maxSize = Size("251MB").right.get))),
        InvalidMaxSizeConstraintError
      )
    }

    Scenario("Create new envelope for a deleted envelope") {
      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
          Some(Json.obj("foo" -> "bar")), Some(envelopeFilesConstraints)),
        EnvelopeAlreadyCreatedError
      )
    }
  }

  Feature("QuarantineFile") {

    Scenario("Quarantine a new file for an open envelope") {
      givenWhenThen(
        envelopeCreated,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        fileQuarantined
      )
    }

    Scenario("Quarantine an additional file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = defaultFileRefId, name = "abc.pdf"),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        fileQuarantined
      )
    }

    Scenario("Quarantine a new file for an existing file id with different fileRefId") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = defaultFileRefId),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        fileQuarantined
      )
    }

    Scenario("Quarantine a new file for a different file id with different fileRefId") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = defaultFileRefId),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        fileQuarantined
      )
    }

    Scenario("Quarantine same file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        FileAlreadyProcessed
      )
    }

    Scenario("Quarantine a new file for a non existing envelope") {
      givenWhenThen(
        --,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeNotFoundError
      )
    }

    Scenario("Quarantine a new file for a deleted envelope") {
      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeNotFoundError
      )
    }

    Scenario("Quarantine a new file for a sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeSealedError
      )
    }

    Scenario("Quarantine a new file for a route requested envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouteRequested,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeRoutingAlreadyRequestedError
      )
    }

    Scenario("Quarantine a new file for a routed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouted,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeAlreadyRoutedError
      )
    }

    Scenario("Quarantine a new file for a archived envelope") {
      givenWhenThen(
        envelopeCreated And envelopeArchived,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()),
        EnvelopeArchivedError
      )
    }
  }

  Feature("MarkFileAsClean") {

    Scenario("Mark file as clean for an existing file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    Scenario("Mark file as clean for an existing file which has other files") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = defaultFileRefId) And fileQuarantined,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    Scenario("Mark file as clean for an already marked file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    Scenario("Mark file as clean for an already stored file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    Scenario("Mark file as clean for an existing fileId with different fileRefId") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = defaultFileRefId),
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    Scenario("Mark file as clean for a non existing file") {
      givenWhenThen(
        envelopeCreated,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    Scenario("Mark file as clean for a sealed file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    Scenario("Mark file as clean for an archived envelope") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        EnvelopeArchivedError
      )
    }
  }

  Feature("MarkFileAsInfected") {

    Scenario("Mark file as infected for an existing file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    Scenario("Mark file as clean for an existing file which has other files") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = defaultFileRefId) And fileQuarantined,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    Scenario("Mark file as infected for an already infected file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    Scenario("Mark file as clean for an already stored file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    Scenario("Mark file as infected for an existing fileId with different fileRefId") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = defaultFileRefId),
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    Scenario("Mark file as infected for a non existing file") {
      givenWhenThen(
        envelopeCreated,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    Scenario("Mark file as infected for a sealed file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    Scenario("Mark file as infected for an archived envelope") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        EnvelopeArchivedError
      )
    }
  }

  Feature("StoreFile") {

    Scenario("Store file for an existing file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored
      )
    }

    Scenario("Store file for an existing file which is not scanned yet") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    Scenario("Store file for an already stored file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileAlreadyProcessed
      )
    }

    Scenario("Store file for an existing file with virus should fail") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileWithError
      )
    }

    Scenario("Store file for an existing file and a sealed envelope") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And envelopeSealed,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored And envelopeRouteRequested
      )
    }

    Scenario("Store file for an existing file and another quarantined file and a sealed envelope") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = defaultFileRefId) And fileQuarantined And noVirusDetected And envelopeSealed,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored
      )
    }

    Scenario("Store file for an existing fileId with different fileRefId") {
      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = defaultFileRefId),
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    Scenario("Store file for a non existing file") {
      givenWhenThen(
        envelopeCreated,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    Scenario("Store file for a non existing file ref") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        StoreFile(envelopeId, fileId, fileRefId.copy(fileRefId.value + "_"), 100),
        FileNotFoundError
      )
    }

    Scenario("Store file for an archived envelope") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        EnvelopeArchivedError
      )
    }
  }

  Feature("DeleteFile") {

    Scenario("Delete file for an existing file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        DeleteFile(envelopeId, fileId),
        fileDeleted
      )
    }

    Scenario("Delete file for a non existing file") {
      givenWhenThen(
        envelopeCreated,
        DeleteFile(envelopeId, fileId),
        FileNotFoundError
      )
    }

    Scenario("Delete file for a sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        DeleteFile(envelopeId, fileId),
        EnvelopeSealedError
      )
    }
  }

  Feature("DeleteEnvelope") {

    Scenario("Delete envelope") {
      givenWhenThen(
        envelopeCreated,
        DeleteEnvelope(envelopeId),
        envelopeDeleted
      )
    }

    Scenario("Delete non existing envelope") {
      givenWhenThen(
        --,
        DeleteEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    Scenario("Delete sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        DeleteEnvelope(envelopeId),
        EnvelopeSealedError
      )
    }

    Scenario("Delete a route requested envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouteRequested,
        DeleteEnvelope(envelopeId),
        EnvelopeRoutingAlreadyRequestedError
      )
    }

    Scenario("Delete routed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouted,
        DeleteEnvelope(envelopeId),
        EnvelopeAlreadyRoutedError
      )
    }

    Scenario("Delete archived envelope") {
      givenWhenThen(
        envelopeCreated And envelopeArchived,
        DeleteEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }

  Feature("SealEnvelope") {

    Scenario("Seal envelope") {
      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouteRequested
      )
    }

    Scenario("Seal envelope with quarantined file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed
      )
    }

    Scenario("Seal envelope with exceeding max item count") {
      val eventsBuffer = ListBuffer[EventData]()
      eventsBuffer+=envelopeCreatedWithLimitedMaxItemConstraint(envelopeFilesConstraints.copy(maxItems = 2))
      for(x <- 1 to 5){
        eventsBuffer += FileQuarantined(envelopeId, FileId(s"fileId-$x"), FileRefId(s"fileRefId-$x"), 0, "test.pdf", "pdf", Some(123L), Json.obj())
      }
      givenWhenThen(
        eventsBuffer.toList,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeItemCountExceededError(2,5)
      )
    }

    Scenario("Seal envelope with no virus detected file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed
      )
    }

    Scenario("Seal envelope with virus detected file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        FilesWithError(List(virusDetected.fileId))
      )
    }

    Scenario("Seal envelope with stored file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouteRequested
      )
    }

    Scenario("Seal envelope with no files") {
      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouteRequested
      )
    }

    Scenario("Seal envelope with a different destination") {
      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DESTINATION_X", "testApplication"),
        envelopeSealed.copy(destination = "DESTINATION_X") And envelopeRouteRequested
      )
    }

    Scenario("Seal non existing envelope") {
      givenWhenThen(
        --,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeNotFoundError
      )
    }

    Scenario("Seal deleted envelope") {
      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeNotFoundError
      )
    }

    Scenario("Seal sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeSealedError
      )
    }

    Scenario("Seal for a route requested envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouteRequested,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeRoutingAlreadyRequestedError
      )
    }

    Scenario("Seal routed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouted,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeAlreadyRoutedError
      )
    }

    Scenario("Seal archived envelope") {
      givenWhenThen(
        envelopeCreated And envelopeArchived,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeArchivedError
      )
    }
  }

  Feature("UnsealEnvelope") {

    Scenario("Unseal sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    Scenario("Unseal envelope with quarantined file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    Scenario("Unseal envelope with no virus detected file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    Scenario("Unseal envelope with stored file") {
      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    Scenario("Unseal non existing envelope") {
      givenWhenThen(
        --,
        UnsealEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    Scenario("Unseal deleted envelope") {
      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        UnsealEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    Scenario("Unseal for a route requested envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouteRequested,
        UnsealEnvelope(envelopeId),
        EnvelopeRoutingAlreadyRequestedError
      )
    }

    Scenario("Unseal routed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouted,
        UnsealEnvelope(envelopeId),
        EnvelopeAlreadyRoutedError
      )
    }

    Scenario("Unseal archived envelope") {
      givenWhenThen(
        envelopeCreated And envelopeArchived,
        UnsealEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }

  Feature("ArchiveEnvelope") {

    Scenario("Archive routed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeRouted,
        ArchiveEnvelope(envelopeId),
        envelopeArchived
      )
    }

    Scenario("Archive open envelope") {
      givenWhenThen(
        envelopeCreated,
        ArchiveEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    Scenario("Archive non existing envelope") {
      givenWhenThen(
        --,
        ArchiveEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    Scenario("Archive sealed envelope") {
      givenWhenThen(
        envelopeCreated And envelopeSealed,
        ArchiveEnvelope(envelopeId),
        EnvelopeSealedError
      )
    }

    Scenario("Archive archived envelope") {
      givenWhenThen(
        envelopeCreated And envelopeArchived,
        ArchiveEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }
}
