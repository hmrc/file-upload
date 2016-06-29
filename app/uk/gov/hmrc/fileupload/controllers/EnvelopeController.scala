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
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.fileupload.actors.{Actors, EnvelopeSealedException, EnvelopeService, Marshaller}
import uk.gov.hmrc.fileupload.actors.Implicits.FutureUtil
import uk.gov.hmrc.fileupload.models.{Envelope, EnvelopeFactory, EnvelopeNotFoundException}
import uk.gov.hmrc.play.microservice.controller.BaseController
import akka.pattern._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import Envelope._
import Marshaller._
import EnvelopeService._

import scala.util.Success


object EnvelopeController extends BaseController {

  implicit val system = Actors.actorSystem
  implicit val executionContext = system.dispatcher
  implicit val defaultTimeout = Timeout(2 seconds)

  val envelopeService = Actors.envelopeService
  val marshaller = Actors.marshaller

  def create(envelopeFactory: EnvelopeFactory = new EnvelopeFactory()) = Action.async { implicit request =>

    def envelopeLocation = (id: String) => LOCATION -> s"${request.host}${routes.EnvelopeController.show(id)}"

    implicit val createConstraintsReads: Format[CreateConstraints] = Json.format[CreateConstraints]
    implicit val createEnvelopeReads: Format[CreateEnvelope] = Json.format[CreateEnvelope]

    val envelope: Envelope = request.body.asJson.map(Json.fromJson[CreateEnvelope](_)) match {
      case Some(result) => envelopeFactory.fromCreateEnvelope(result.get)
      case None => envelopeFactory.emptyEnvelope()
    }

    (envelopeService ? NewEnvelope(envelope))
      .breakOnFailure
      .map { case true => Created.withHeaders(envelopeLocation(envelope._id)) }
      .recover { case e => ExceptionHandler(e) }
  }

  def show(id: String) = Action.async {

    def findEnvelopeFor = (id: String) => (envelopeService ? GetEnvelope(id))
      .breakOnFailure
      .mapTo[Envelope]

    def toJson = (e: Envelope) => marshaller ? e
    def onEnvelopeFound: (Any) => Result = {
      case json: JsValue => Ok(json)
    }

    findEnvelopeFor(id)
      .flatMap(toJson)
      .breakOnFailure
      .map(onEnvelopeFound)
      .recover { case e => ExceptionHandler(e) }
  }

  def delete(id: String) = Action.async {

    def deleteEnvelope = (id: String) => envelopeService ? DeleteEnvelope(id)
    def onEnvelopeDeleted: (Any) => Result = {
      case Success(true) => Accepted
      case Success(false) => throw new EnvelopeNotFoundException(id)
      case e: EnvelopeSealedException => throw e
    }

    deleteEnvelope(id)
      .map(onEnvelopeDeleted)
      .recover { case e => ExceptionHandler(e) }
  }


}
