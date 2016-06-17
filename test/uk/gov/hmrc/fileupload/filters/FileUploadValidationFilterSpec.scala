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

import akka.testkit.TestActorRef
import org.mockito.Mockito
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{Results, EssentialAction, Result, RequestHeader}
import uk.gov.hmrc.fileupload.Support
import uk.gov.hmrc.fileupload.actors.{FileUploadTestActors, ActorStub, Actors}
import uk.gov.hmrc.fileupload.controllers.BadRequestException
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}

import scala.concurrent.Future

class FileUploadValidationFilterSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

	import Support.Implicits._
	import Mockito._

	val envelopeId = "bd7a685a-9525-4868-9161-bd1a9531394f"
	val fileId = "uploaded.scala"

  "filter" should {
    "return a failure when fileId is already contained in the envelope" in {
	    val nextFilter: (RequestHeader) => Future[Result] = (requestHeader) => Future.successful(Results.Ok)
	    val envelopeService: ActorStub = FileUploadTestActors.envelopeService
			envelopeService.setReply(Support.envelope.copy(files = Some(Seq("uploaded.scala")) ))

	    val requestHeader = mock[RequestHeader]

	    Mockito.when(requestHeader.path).thenReturn(s"/file-upload/envelope/$envelopeId/file/$fileId/content")
	    when(requestHeader.method).thenReturn("PUT")

	    await( FileUploadValidationFilter(nextFilter)(requestHeader) ) shouldBe Results.BadRequest
    }

	  "return a future when fileId is not already contained in the envelope" in {
		  val nextFilter: (RequestHeader) => Future[Result] = (requestHeader) => Future.successful(Results.Ok)
		  val envelopeService: ActorStub = FileUploadTestActors.envelopeService
		  envelopeService.setReply(Support.envelope)

		  val requestHeader = mock[RequestHeader]

		  Mockito.when(requestHeader.path).thenReturn(s"/file-upload/envelope/$envelopeId/file/$fileId/content")
		  when(requestHeader.method).thenReturn("PUT")

		  await( FileUploadValidationFilter(nextFilter)(requestHeader) ) shouldBe Results.Ok
	  }

	  "return not found when the specified envelope is not in the storage" in {
		  val nextFilter: (RequestHeader) => Future[Result] = (requestHeader) => Future.successful(Results.Ok)
		  val envelopeService: ActorStub = FileUploadTestActors.envelopeService
		  envelopeService.setReply(new BadRequestException(s"no envelope exists for id:$envelopeId"))

		  val requestHeader = mock[RequestHeader]

		  Mockito.when(requestHeader.path).thenReturn(s"/file-upload/envelope/$envelopeId/file/$fileId/content")
		  when(requestHeader.method).thenReturn("PUT")

		  await( FileUploadValidationFilter(nextFilter)(requestHeader) ) shouldBe Results.NotFound
	  }
  }

}