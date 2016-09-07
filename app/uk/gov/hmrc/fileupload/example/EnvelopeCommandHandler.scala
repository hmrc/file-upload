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

import uk.gov.hmrc.fileupload.domain.{Repository, Version}

class EnvelopeCommandHandler(repository: Repository[Envelope]) {

  def handle(command: AnyRef) = {
    println(s"Command $command received")
    command match {
      case c: CreateEnvelope =>
        val envelope = new Envelope(c.id)
        envelope.create(c.id)
        repository.save(envelope, Version(1))

      case c: QurantineFile =>
        val envelope = repository.byId(c.id)
        envelope.quarantineFile(c.fileId, c.fileReferenceId)
        repository.save(envelope, Version(1))

      case c: CleanFile =>
        val envelope = repository.byId(c.id)
        envelope.cleanFile(c.fileId, c.fileReferenceId)
        repository.save(envelope, Version(1))
    }
  }
}
