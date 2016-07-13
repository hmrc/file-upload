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
import uk.gov.hmrc.fileupload.envelope.Repository
import uk.gov.hmrc.fileupload.{MicroserviceGlobal, file}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object DatabaseController extends BaseController {

  implicit val ec = ExecutionContext.global

  def delete() = Action { implicit request =>
    val mongo = MicroserviceGlobal.db
    new Repository(mongo).removeAll()
    new file.Repository(mongo).removeAll()
    Ok
  }

}
