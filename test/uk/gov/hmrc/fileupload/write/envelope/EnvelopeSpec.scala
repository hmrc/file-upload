/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{Constraints, DefaultEnvelopeConstraints}
import uk.gov.hmrc.fileupload.{EnvelopeId, EventBasedGWTSpec, FileId, FileRefId}

class EnvelopeSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope] {

  val defaultContentTypes = "application/pdf,image/jpeg,application/xml"
  val acceptedContentTypes = "application/pdf,image/jpeg,application/xml,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  val defaultEnvelopeConstraints = DefaultEnvelopeConstraints(defaultContentTypes, acceptedContentTypes)

  override val handler = new EnvelopeHandler(defaultEnvelopeConstraints)

  override val defaultStatus: Envelope = Envelope()

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val defaultConstraints = Constraints(Some(defaultContentTypes))

  val envelopeCreated = EnvelopeCreated(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), defaultConstraints)
  val fileQuarantined = FileQuarantined(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj())
  val noVirusDetected = NoVirusDetected(envelopeId, fileId, fileRefId)
  val virusDetected = VirusDetected(envelopeId, fileId, fileRefId)
  val fileStored = FileStored(envelopeId, fileId, fileRefId, 100)
  val fileDeleted = FileDeleted(envelopeId, fileId)
  val envelopeDeleted = EnvelopeDeleted(envelopeId)
  val envelopeSealed = EnvelopeSealed(envelopeId, "testRoutingRequestId", "DMS", "testApplication")
  val envelopeUnsealed = EnvelopeUnsealed(envelopeId)
  val envelopeRouted = EnvelopeRouted(envelopeId)
  val envelopeArchived = EnvelopeArchived(envelopeId)

  feature("CreateEnvelope") {

    scenario("Create new envelope") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), defaultConstraints),
        envelopeCreated
      )
    }

    scenario("Create new envelope for an existing envelope") {

      givenWhenThen(
        envelopeCreated,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), defaultConstraints),
        EnvelopeAlreadyCreatedError
      )
    }

    scenario("Create new envelope for a deleted envelope") {

      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), defaultConstraints),
        EnvelopeAlreadyCreatedError
      )
    }
  }

  feature("QuarantineFile") {

    scenario("Quarantine a new file for an open envelope") {

      givenWhenThen(
        envelopeCreated,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        fileQuarantined
      )
    }

    scenario("Quarantine an additional file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = FileRefId(), name = "abc.pdf"),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        fileQuarantined
      )
    }

    scenario("Quarantine a new file for an existing file id with different fileRefId") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = FileRefId()),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        fileQuarantined
      )
    }

    scenario("Quarantine a new file for a different file id with different fileRefId") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = FileRefId()),
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        fileQuarantined
      )
    }

    scenario("Quarantine same file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        FileAlreadyProcessed
      )
    }

    scenario("Quarantine a new file for a non existing envelope") {

      givenWhenThen(
        --,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        EnvelopeNotFoundError
      )
    }

    scenario("Quarantine a new file for a deleted envelope") {

      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        EnvelopeNotFoundError
      )
    }

    scenario("Quarantine a new file for a sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        EnvelopeSealedError
      )
    }

    scenario("Quarantine a new file for a routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        EnvelopeAlreadyRoutedError
      )
    }

    scenario("Quarantine a new file for a archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()),
        EnvelopeArchivedError
      )
    }
  }

  feature("MarkFileAsClean") {

    scenario("Mark file as clean for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    scenario("Mark file as clean for an existing file which has other files") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = FileRefId()) And fileQuarantined,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    scenario("Mark file as clean for an already marked file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    scenario("Mark file as clean for an already stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    scenario("Mark file as clean for an existing fileId with different fileRefId") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = FileRefId()),
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    scenario("Mark file as clean for a non existing file") {

      givenWhenThen(
        envelopeCreated,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    scenario("Mark file as clean for a sealed file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        noVirusDetected
      )
    }

    scenario("Mark file as clean for an archived envelope") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        MarkFileAsClean(envelopeId, fileId, fileRefId),
        EnvelopeArchivedError
      )
    }
  }

  feature("MarkFileAsInfected") {

    scenario("Mark file as infected for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    scenario("Mark file as clean for an existing file which has other files") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = FileRefId()) And fileQuarantined,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    scenario("Mark file as infected for an already infected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    scenario("Mark file as clean for an already stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileAlreadyProcessed
      )
    }

    scenario("Mark file as infected for an existing fileId with different fileRefId") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = FileRefId()),
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    scenario("Mark file as infected for a non existing file") {

      givenWhenThen(
        envelopeCreated,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        FileNotFoundError
      )
    }

    scenario("Mark file as infected for a sealed file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
      )
    }

    scenario("Mark file as infected for an archived envelope") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        EnvelopeArchivedError
      )
    }
  }

  feature("StoreFile") {

    scenario("Store file for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored
      )
    }

    scenario("Store file for an existing file which is not scanned yet") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    scenario("Store file for an already stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileAlreadyProcessed
      )
    }

    scenario("Store file for an existing file with virus should fail") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileWithError
      )
    }

    scenario("Store file for an existing file and a sealed envelope") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And envelopeSealed,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored And envelopeRouted
      )
    }

    scenario("Store file for an existing file and another quarantined file and a sealed envelope") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileId = FileId(), fileRefId = FileRefId()) And fileQuarantined And noVirusDetected And envelopeSealed,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored
      )
    }

    scenario("Store file for an existing fileId with different fileRefId") {

      givenWhenThen(
        envelopeCreated And fileQuarantined.copy(fileRefId = FileRefId()),
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    scenario("Store file for a non existing file") {

      givenWhenThen(
        envelopeCreated,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        FileNotFoundError
      )
    }

    scenario("Store file for a non existing file ref") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        StoreFile(envelopeId, fileId, fileRefId.copy(fileRefId.value + "_"), 100),
        FileNotFoundError
      )
    }

    scenario("Store file for an archived envelope") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And fileStored And envelopeSealed And envelopeRouted And envelopeArchived,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        EnvelopeArchivedError
      )
    }
  }

  feature("DeleteFile") {

    scenario("Delete file for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        DeleteFile(envelopeId, fileId),
        fileDeleted
      )
    }

    scenario("Delete file for a non existing file") {

      givenWhenThen(
        envelopeCreated,
        DeleteFile(envelopeId, fileId),
        FileNotFoundError
      )
    }

    scenario("Delete file for a sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        DeleteFile(envelopeId, fileId),
        EnvelopeSealedError
      )
    }
  }

  feature("DeleteEnvelope") {

    scenario("Delete envelope") {

      givenWhenThen(
        envelopeCreated,
        DeleteEnvelope(envelopeId),
        envelopeDeleted
      )
    }

    scenario("Delete non existing envelope") {

      givenWhenThen(
        --,
        DeleteEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    scenario("Delete sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        DeleteEnvelope(envelopeId),
        EnvelopeSealedError
      )
    }

    scenario("Delete routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        DeleteEnvelope(envelopeId),
        EnvelopeAlreadyRoutedError
      )
    }

    scenario("Delete archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        DeleteEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }

  feature("SealEnvelope") {

    scenario("Seal envelope") {

      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouted
      )
    }

    scenario("Seal envelope with quarantined file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed
      )
    }

    scenario("Seal envelope with no virus detected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed
      )
    }

    scenario("Seal envelope with virus detected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        FilesWithError(List(virusDetected.fileId))
      )
    }

    scenario("Seal envelope with stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouted
      )
    }

    scenario("Seal envelope with no files") {

      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        envelopeSealed And envelopeRouted
      )
    }

    scenario("Seal envelope with a different destination") {

      givenWhenThen(
        envelopeCreated,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DESTINATION_X", "testApplication"),
        envelopeSealed.copy(destination = "DESTINATION_X") And envelopeRouted
      )
    }

    scenario("Seal non existing envelope") {

      givenWhenThen(
        --,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeNotFoundError
      )
    }

    scenario("Seal deleted envelope") {

      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeNotFoundError
      )
    }

    scenario("Seal sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeSealedError
      )
    }

    scenario("Seal routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeAlreadyRoutedError
      )
    }

    scenario("Seal archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        SealEnvelope(envelopeId, "testRoutingRequestId", "DMS", "testApplication"),
        EnvelopeArchivedError
      )
    }
  }

  feature("UnsealEnvelope") {

    scenario("Unseal sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    scenario("Unseal envelope with quarantined file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    scenario("Unseal envelope with no virus detected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    scenario("Unseal envelope with stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored And envelopeSealed,
        UnsealEnvelope(envelopeId),
        envelopeUnsealed
      )
    }

    scenario("Unseal non existing envelope") {

      givenWhenThen(
        --,
        UnsealEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    scenario("Unseal deleted envelope") {

      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        UnsealEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    scenario("Unseal routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        UnsealEnvelope(envelopeId),
        EnvelopeAlreadyRoutedError
      )
    }

    scenario("Unseal archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        UnsealEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }

  feature("ArchiveEnvelope") {

    scenario("Archive routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        ArchiveEnvelope(envelopeId),
        envelopeArchived
      )
    }

    scenario("Archive open envelope") {

      givenWhenThen(
        envelopeCreated,
        ArchiveEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    scenario("Archive non existing envelope") {

      givenWhenThen(
        --,
        ArchiveEnvelope(envelopeId),
        EnvelopeNotFoundError
      )
    }

    scenario("Archive sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        ArchiveEnvelope(envelopeId),
        EnvelopeSealedError
      )
    }

    scenario("Archive archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        ArchiveEnvelope(envelopeId),
        EnvelopeArchivedError
      )
    }
  }
}
