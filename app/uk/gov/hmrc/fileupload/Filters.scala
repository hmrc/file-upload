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

import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import com.codahale.metrics.MetricRegistry
import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.play.bootstrap.backend.filters.BackendFilters
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}

import scala.concurrent.ExecutionContext


@Singleton
class Filters @Inject()(
  backendFilters: BackendFilters,
  metricRegistry: MetricRegistry
)(implicit
  mat: Materializer,
  ec: ExecutionContext
) extends HttpFilters {

    val userAgentRequestFilter = new UserAgentRequestFilter(
      metricRegistry,
      userAgentAllowlist = UserAgent.allKnown,
      userAgentIgnoreList = UserAgent.defaultIgnoreList
    )

    def filters: Seq[EssentialFilter] =
      userAgentRequestFilter +: backendFilters.filters
}
