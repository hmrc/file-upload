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

package uk.gov.hmrc.fileupload.filters

import akka.util.Timeout
import play.api.mvc.{Results, Result, RequestHeader, Filter}
import uk.gov.hmrc.fileupload.actors.Actors
import uk.gov.hmrc.fileupload.actors.EnvelopeService.GetEnvelope
import uk.gov.hmrc.fileupload.models.Envelope
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import scala.language.postfixOps
import uk.gov.hmrc.fileupload.actors.Implicits.FutureUtil

object FileUploadValidationFilter extends Filter {

	implicit val defaultTimeout = Timeout(2 seconds)
	implicit val ec = ExecutionContext.global

	val envelopeService = Actors.envelopeService

	override  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

		val putFilePattern = "/file-upload/envelope/(.*)/file/(.*)/content".r

		(requestHeader.method, requestHeader.path) match {
			case ("PUT", putFilePattern(envelopeId, fileId)) =>
				(envelopeService ? GetEnvelope(envelopeId))
					.breakOnFailure
					.flatMap {
						case e: Envelope if e.contains(fileId)  => Future.successful(Results.BadRequest)
						case _ => nextFilter(requestHeader)
					}.recover{ case e => Results.NotFound }
			case _ => nextFilter(requestHeader)
		}
	}
}
