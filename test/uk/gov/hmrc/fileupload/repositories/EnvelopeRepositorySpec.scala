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

package uk.gov.hmrc.fileupload.repositories

import org.joda.time.DateTime
import uk.gov.hmrc.fileupload.models.{Constraints, Envelope}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import scala.concurrent.ExecutionContext.Implicits.global

class EnvelopeRepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication  {

  "repository" should {
    "persist an envelope" in {
      val contraints = Constraints(contentTypes = Seq("contenttype1"), maxItems = 3, maxSize = "1GB", maxSizePerItem = "100MB" )

      val expiryDate = new DateTime().plusDays(2)
      val envelope = Envelope(constraints = contraints, callbackUrl = "http://localhost/myendpoint", expiryDate = expiryDate, metadata = Map("a" -> "1") )

      val repository = new EnvelopeRepository
      val result = await(repository persist envelope)
      result.hasErrors shouldBe false

    }
  }



}
