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

package uk.gov.hmrc.fileupload.testonly

import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Results._
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.fileupload.read.file.{Repository => FileRepository}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, Repository => EnvelopeRepository}

import scala.concurrent.{ExecutionContext, Future}

class TestOnlyController(fileRepo: FileRepository, envelopeRepo: EnvelopeRepository, findAllEnvelopes: () => Future[List[Envelope]])(implicit executionContext: ExecutionContext) {

  def cleanup() = Action.async { request =>
    val removeEnvelopes: Future[WriteResult] = envelopeRepo.removeAll()
    val removeFiles: Future[List[WriteResult]] = fileRepo.removeAll()

    val cleanUpDb: Future[(WriteResult, List[WriteResult])] = removeEnvelopes zip removeFiles

    cleanUpDb.map { results =>
      if (results._2.forall(_.ok)) {
        Ok
      } else {
        InternalServerError
      }
    }
  }

  def stats() = Action.async {
    findAllEnvelopes().map(envelopes => {
      val envelopesSize = envelopes.size
      val filesSize = envelopes.map( _.files.size).sum
      Ok(Json.obj("envelopes"->envelopesSize, "files" -> filesSize))
    })
  }

}
