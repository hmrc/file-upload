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

import uk.gov.hmrc.fileupload.domain.InMemoryEventStore
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}

object Runner extends App {

  //this we can create inside microserviceGlobal
  implicit val eventStore = new InMemoryEventStore()
  implicit val publish = (e: AnyRef) => {
    println(s"$e published")
  }
  val handle = CommandHandler.handleCommand _

  val serviceWhichCallsCommandFunc = serviceWhichCallsCommand(handle) _

  serviceWhichCallsCommandFunc(new CreateEnvelope(EnvelopeId("envelope-id-1")))
  serviceWhichCallsCommandFunc(new QurantineFile(EnvelopeId("envelope-id-1"), FileId("file-id-1"), FileReferenceId("file-reference-id-1")))
  serviceWhichCallsCommandFunc(new CleanFile(EnvelopeId("envelope-id-1"), FileId("file-id-1"), FileReferenceId("file-reference-id-1")))


  def serviceWhichCallsCommand(handle: (EnvelopeCommand) => Unit)(command: EnvelopeCommand) =
    handle(command)
}
