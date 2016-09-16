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

package uk.gov.hmrc.fileupload.write.envelope

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.{EnvelopeId, EventBasedGWTSpec, FileId, FileRefId}

class EnvelopeSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope, EnvelopeCommandNotAccepted] {

  override val handler = Envelope

  override val defaultStatus: Envelope = Envelope()

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val envelopeCreated = EnvelopeCreated(envelopeId, Some("http://www.callback-url.com"))
  val fileQuarantined = FileQuarantined(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj())
  val noVirusDetected = NoVirusDetected(envelopeId, fileId, fileRefId)
  val virusDetected = VirusDetected(envelopeId, fileId, fileRefId)
  val fileStored = FileStored(envelopeId, fileId, fileRefId, 100)
  val fileDeleted = FileDeleted(envelopeId, fileId)
  val envelopeDeleted = EnvelopeDeleted(envelopeId)
  val envelopeSealed = EnvelopeSealed(envelopeId, "testDestination")
  val envelopeRouted = EnvelopeRouted(envelopeId)
  val envelopeArchived = EnvelopeArchived(envelopeId)

  feature("CreateEnvelope") {

    scenario("Create new envelope") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com")),
        envelopeCreated
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
        EnvelopeSealedError
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
  }

  feature("MarkFileAsInfected") {

    scenario("Mark file as infected for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        MarkFileAsInfected(envelopeId, fileId, fileRefId),
        virusDetected
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
  }

  feature("StoreFile") {

    scenario("Store file for an existing file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored
      )
    }

    scenario("Store file for an existing file and a sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed And fileQuarantined And noVirusDetected,
        StoreFile(envelopeId, fileId, fileRefId, 100),
        fileStored And envelopeRouted
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
        EnvelopeSealedError
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
        SealEnvelope(envelopeId, "testDestination"),
        envelopeSealed
      )
    }

    scenario("Seal envelope with quarantined file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined,
        SealEnvelope(envelopeId, "testDestination"),
        envelopeSealed
      )
    }

    scenario("Seal envelope with no virus detected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And noVirusDetected,
        SealEnvelope(envelopeId, "testDestination"),
        envelopeSealed
      )
    }

    scenario("Seal envelope with virus detected file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And virusDetected,
        SealEnvelope(envelopeId, "testDestination"),
        FilesWithError(List(virusDetected.fileId))
      )
    }

    scenario("Seal envelope with stored file") {

      givenWhenThen(
        envelopeCreated And fileQuarantined And fileStored,
        SealEnvelope(envelopeId, "testDestination"),
        envelopeSealed
      )
    }

    scenario("Seal non existing envelope") {

      givenWhenThen(
        --,
        SealEnvelope(envelopeId, "testDestination"),
        EnvelopeNotFoundError
      )
    }

    scenario("Seal deleted envelope") {

      givenWhenThen(
        envelopeCreated And envelopeDeleted,
        SealEnvelope(envelopeId, "testDestination"),
        EnvelopeNotFoundError
      )
    }

    scenario("Seal sealed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeSealed,
        SealEnvelope(envelopeId, "testDestination"),
        EnvelopeSealedError
      )
    }

    scenario("Seal routed envelope") {

      givenWhenThen(
        envelopeCreated And envelopeRouted,
        SealEnvelope(envelopeId, "testDestination"),
        EnvelopeSealedError
      )
    }

    scenario("Seal archived envelope") {

      givenWhenThen(
        envelopeCreated And envelopeArchived,
        SealEnvelope(envelopeId, "testDestination"),
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
