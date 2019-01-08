/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject

import akka.stream.Materializer
import com.codahale.metrics.MetricRegistry
import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

case class UserAgent(value: String) extends AnyVal
object UserAgent {
  val allKnown = Set(
    UserAgent("voa-property-linking"),
    UserAgent("voa-property-linking-frontend"),
    UserAgent("dfs-frontend"),
    UserAgent("FU-frontend-CH"),
    UserAgent("FU-frontend-transfer"),
    UserAgent("business-rates-check"),
    UserAgent("business-rates-check-frontend"))
  val defaultIgnoreList = Set(UserAgent("nginx-health"), UserAgent("docktor"))
  val noUserAgent = UserAgent("NoUserAgent")
  val unknownUserAgent = UserAgent("UnknownUserAgent")
}

class UserAgentRequestFilter @Inject()(metricRegistry: MetricRegistry,
                                       userAgentWhitelist: Set[UserAgent],
                                       userAgentIgnoreList: Set[UserAgent])(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    def timeWith(userAgent: UserAgent): Future[Result] = {
      val timer = metricRegistry.timer(s"request.user-agent.${userAgent.value}")
      val context = timer.time()
      nextFilter(rh).map { res =>
        context.stop()
        res
      }
    }

    rh.headers.get(HeaderNames.USER_AGENT).map(UserAgent.apply) match {
      case Some(ua) if userAgentIgnoreList.contains(ua) =>
        nextFilter(rh)

      case Some(ua) if userAgentWhitelist.contains(ua) =>
        timeWith(ua)

      case Some(unknownUserAgent) => {
        Logger.info(s"Agent $unknownUserAgent is not in UserAgentRequestFilter whitelist for ${rh.path}")
        timeWith(UserAgent.unknownUserAgent)
      }

      case None => timeWith(UserAgent.noUserAgent)
    }
  }

}
