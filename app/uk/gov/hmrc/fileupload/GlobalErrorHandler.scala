/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload

import javax.inject.Inject
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler

import scala.concurrent.{ExecutionContext, Future}

class GlobalErrorHandler @Inject() extends HttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future(ExceptionHandler(statusCode, message))(ExecutionContext.global)

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    Future(ExceptionHandler(exception))(ExecutionContext.global)
}
