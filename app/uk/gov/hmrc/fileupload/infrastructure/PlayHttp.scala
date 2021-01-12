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

package uk.gov.hmrc.fileupload.infrastructure

import java.net.URL

import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.mvc.Headers
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

object PlayHttp {

  case class PlayHttpError(message: String)

  def execute(connector: AuditConnector, appName: String, errorLogger: Option[(Throwable => Unit)])(request: WSRequest)
             (implicit ec: ExecutionContext): Future[Either[PlayHttpError, WSResponse]] = {
    val hc = headerCarrier(request)
    val eventualResponse = request.execute()

    eventualResponse.foreach {
      response => {
        val path = new URL(request.url).getPath
        connector.sendEvent(DataEvent(appName, EventTypes.Succeeded,
          tags = Map("method" -> request.method, "statusCode" -> s"${ response.status }", "responseBody" -> response.body)
            ++ hc.toAuditTags(path, path),
          detail = hc.toAuditDetails()))
      }
    }
    eventualResponse.map(Right.apply)
      .recover {
        case NonFatal(t) =>
          errorLogger.foreach(log => log(t))
          Left(PlayHttpError(t.getMessage))
      }
  }

  private def headerCarrier(request: WSRequest): HeaderCarrier = {
    HeaderCarrierConverter.fromHeadersAndSession(new Headers(request.headers.toSeq.map{ case (s, seq) => (s, seq.head) }))
  }
}
