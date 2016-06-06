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

import java.io.{PrintWriter, StringWriter}

import akka.util.Timeout
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.actors.{EnvelopeManager, Actors}
import uk.gov.hmrc.fileupload.models.Envelope
import uk.gov.hmrc.play.microservice.controller.BaseController
import akka.pattern._
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.{ExecutionContext, Future}

object EnvelopeController extends BaseController {
  import Envelope._

  implicit val system = Actors.actorSystem
  val envelopeManager = Actors.envelopeMgr
  implicit val ec = system.dispatcher
  implicit val defaultTimeout = Timeout(2 second)

  def create() = Action.async { implicit request =>

    val json = request.body.asJson.getOrElse( throw new Exception)

	  for{
	   res <- envelopeManager ? EnvelopeManager.CreateEnvelope(json)
		  id =  res.asInstanceOf[BSONObjectID]
	  } yield Ok.withHeaders(LOCATION -> s"${request.host}/${routes.EnvelopeController.show(id.stringify)}")
  }

  def show(id: String) = Action.async{
    ( envelopeManager ? EnvelopeManager.GetEnvelope(id) )
      .map(_.asInstanceOf[Option[Envelope]])
      .map( Json.toJson(_) )
      .map( Ok(_))
      .recover{ case t => InternalServerError  }
  }
}
