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

package uk.gov.hmrc.fileupload.controllers


import akka.util.Timeout
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.actors.{EnvelopeService, Actors}
import uk.gov.hmrc.fileupload.actors.Implicits.FutureUtil
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.play.microservice.controller.BaseController
import akka.pattern._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import Envelope._
import EnvelopeService._

object EnvelopeController extends BaseController {

  implicit val system = Actors.actorSystem
  implicit val executionContext = system.dispatcher
  implicit val defaultTimeout = Timeout(2 second)
  val envelopeService = Actors.envelopeService

  def create() = Action.async { implicit request =>

	  def fromRequestBody = () => Future(request.body.asJson.getOrElse( throw new Exception))
	  def createEnvelope = (json: JsValue) => envelopeService ? CreateEnvelope(json)
	  def envelopeLocation = (id: String) => LOCATION -> s"${request.host}${routes.EnvelopeController.show(id)}"
	  def onEnvelopeCreated = (any: Any) => mapToResult(any) { case id: String => Created.withHeaders(envelopeLocation(id)) }

	  fromRequestBody()
		  .flatMap(createEnvelope)
      .breakOnFailure
		  .map(onEnvelopeCreated)
		 .recover{ case e => ExceptionHandler(e) }
  }

  def show(id: String) = Action.async{

	  def findEnvelopeFor = (id: String) =>  envelopeService ? GetEnvelope(id)
	  def onEnvelopeFound = (any: Any) => mapToResult(any){case json: JsValue => Ok(json) }

    findEnvelopeFor(id)
      .flattenTry
	    .map(onEnvelopeFound)
      .recover{ case e => ExceptionHandler(e)  }
  }

	def delete(id: String) = Action.async {

		def deleteEnvelope = (id: String) => envelopeService ? DeleteEnvelope(id)
		def onEnvelopeDeleted = (any: Any) => mapToResult(any) {
			case true => Ok
			case false => NotFound
		}

		deleteEnvelope(id)
		  .map(onEnvelopeDeleted)
		  .recover { case e =>  ExceptionHandler(e) }
	}

	def mapToResult(any: Any)(pf: PartialFunction[Any, Result]): Result = {
		pf.orElse[Any, Result]{ case e: Throwable => ExceptionHandler(e) }(any)
	}
}
