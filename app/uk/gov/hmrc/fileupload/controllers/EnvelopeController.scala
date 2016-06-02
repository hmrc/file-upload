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

import play.api.mvc._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

object EnvelopeController extends EnvelopeController

trait EnvelopeController extends BaseController {

  def create() = Action.async { implicit request =>

    val json = request.body.asJson.getOrElse( throw new Exception)
    val id = (json \ "id").as[String]

    Future.successful(Ok.withHeaders(
      LOCATION -> s"http://test.com/file-upload/envelope/$id"
    ))
  }
}
