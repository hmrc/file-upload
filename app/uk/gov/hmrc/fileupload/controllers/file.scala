/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers

import play.api.libs.iteratee.Iteratee
import play.api.mvc.BodyParser
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.utils.StreamUtils.iterateeToAccumulator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object UploadParser {

  def parse(uploadFile: (EnvelopeId, FileId, FileRefId) => Iteratee[ByteStream, Future[JSONReadFile]])
           (envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId)
           (implicit ex: ExecutionContext): BodyParser[Future[JSONReadFile]] = BodyParser { _ =>

    iterateeToAccumulator(uploadFile(envelopeId, fileId, fileRefId)) map (Right(_)) recover {
      case NonFatal(e) => Left(ExceptionHandler(e))
  }
}

}
