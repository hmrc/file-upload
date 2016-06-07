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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.actors.{EnvelopeService, Actors}
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.play.microservice.controller.BaseController
import akka.pattern._
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.Future

object EnvelopeController extends BaseController {
  import Envelope._

  implicit val system = Actors.actorSystem
  val envelopeManager = Actors.envelopeMgr
  implicit val ec = system.dispatcher
  implicit val defaultTimeout = Timeout(2 second)

  def create() = Action.async { implicit request =>

	  def getData = () => request.body.asJson.getOrElse( throw new Exception)
	  def envelopeLocation = (id: BSONObjectID) => LOCATION -> s"${request.host}${routes.EnvelopeController.show(id.stringify)}"
	  def createEnvelope = (json: JsValue) => envelopeManager ? EnvelopeService.CreateEnvelope(json)
	  def onEnvelopeCreated = (any: Any) => any match {
		  case id: BSONObjectID => Ok.withHeaders(envelopeLocation(id))
		  case e: Exception => ExceptionHandler(e)
	  }

	  Future(getData())
		  .flatMap(createEnvelope)
		  .map(onEnvelopeCreated)
		  .mapTo[Result]
		  .recover{ case _ => InternalServerError}
  }

  def show(id: String) = Action.async{
    ( envelopeManager ? EnvelopeService.GetEnvelope(id) )
	    .mapTo[Option[Envelope]]
      .map( Json.toJson(_) )
      .map( Ok(_))
      .recover{ case t => InternalServerError  }
  }
}
