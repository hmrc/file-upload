/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.fileupload.ApplicationModule
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.EventStore.GetResult
import uk.gov.hmrc.fileupload.write.infrastructure.{StreamId, Event => DomainEvent, EventSerializer => _}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  val unitOfWorks: StreamId => Future[GetResult] = appModule.unitOfWorks
  val publishAllEvents: Seq[DomainEvent] => Unit = appModule.publishAllEvents

  implicit val eventWrites = EventSerializer.eventWrite

  def get(streamId: StreamId) = Action.async {
    unitOfWorks(streamId) map {
      case Right(r) =>
        Ok(Json.toJson(r.flatMap(_.events)))
      case Left(e) =>
        ExceptionHandler(INTERNAL_SERVER_ERROR, e.message)
    }
  }

  def replay(streamId: StreamId) = Action.async {
    unitOfWorks(streamId).map {
      case Right(sequence) =>
        publishAllEvents(sequence.flatMap(_.events))
        Ok
      case Left(error) => InternalServerError(s"Unexpected result: ${error.message}")
    }
  }
}
