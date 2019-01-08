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

package uk.gov.hmrc.fileupload.infrastructure

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.play.test.UnitSpec


class BasicAuthSpec extends UnitSpec with ScalaFutures {

  def basic64(s:String): String = {
    BaseEncoding.base64().encode(s.getBytes(Charsets.UTF_8))
  }

  val users = List(User("yuan", "yaunspassword"))
  val module = BasicAuth(BasicAuthEnabled(users))
  val trueResult = module.userAuthorised(Option("Basic " + basic64("yuan:yaunspassword")))
  val falseResult = module.userAuthorised(Option("Basic " + basic64("paul:paulspassword")))
  val nonAuthResult = module.userAuthorised(Option(""))


  "The auth function able to take a list of users (users name and password), and check the coming requests authorization is in the list" should {
    "if the coming authorization is in the users list" in {
      trueResult shouldBe true
    }

    "if the coming authorization is not in the users list" in {
      falseResult shouldBe false
    }

    "if there is no auth" in {
      nonAuthResult shouldBe false
    }

  }
}
