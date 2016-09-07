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

package uk.gov.hmrc.fileupload.example

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}
import uk.gov.hmrc.fileupload.domain.{InMemoryEventStore, Repository}

object Runner extends App {

  val repository = new Repository[Envelope]((envelopeId: EnvelopeId) => new Envelope(envelopeId), new InMemoryEventStore())

  val envelopeCommandHandler = new EnvelopeCommandHandler(repository)

  envelopeCommandHandler.handle(new CreateEnvelope(EnvelopeId("envelope-id-1")))
  envelopeCommandHandler.handle(new QurantineFile(EnvelopeId("envelope-id-1"), FileId("file-id-1"), FileReferenceId("file-reference-id-1")))
  envelopeCommandHandler.handle(new CleanFile(EnvelopeId("envelope-id-1"), FileId("file-id-1"), FileReferenceId("file-reference-id-1")))
}
