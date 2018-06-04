/*
 * Copyright 2018 HM Revenue & Customs
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

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import play.api.http.HeaderNames
import play.api.mvc.Results.Status
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

trait BasicAuth {
  def userAuthorised(credentials: Option[String]): Boolean

  def apply(block: => Future[Result])(implicit request: RequestHeader) = {
    val maybeCredentials = request.headers.get(HeaderNames.AUTHORIZATION)
    if (userAuthorised(maybeCredentials)) {
      block
    } else {
      Future.successful(new Status(403))
    }
  }
}

class AuthorisingBasicAuth(users: List[User]) extends BasicAuth {

  def userAuthorised(credential: Option[String]): Boolean =
    credential.exists(cred =>
      users.exists(user => "Basic " + BaseEncoding.base64().encode((user.name + ":" + user.password).getBytes(Charsets.UTF_8)) == cred)
    )
}

object AlwaysAuthorisedBasicAuth extends BasicAuth {
  override def userAuthorised(credentials: Option[String]): Boolean = true
}

object BasicAuth {

  def apply(config: BasicAuthConfiguration) = {
    config match {
      case BasicAuthDisabled => AlwaysAuthorisedBasicAuth
      case BasicAuthEnabled(users) => new AuthorisingBasicAuth(users)
    }
  }
}

sealed abstract class BasicAuthConfiguration

case object BasicAuthDisabled extends BasicAuthConfiguration

case class BasicAuthEnabled(users: List[User]) extends BasicAuthConfiguration

case class User(name: String, password: String)
