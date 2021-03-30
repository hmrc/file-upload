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

package uk.gov.hmrc.fileupload.support

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.Status
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Random

trait IntegrationSpec
  extends AnyFeatureSpec
     with GivenWhenThen
     with ScalaFutures
     with Matchers
     with Status
     with Eventually
     with FakeConsumingService
     with BeforeAndAfterEach
     with MongoSpecSupport {

  val nextId = () => UUID.randomUUID().toString

  val nextUtf8String = () => Random.nextString(36)

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  override def afterAll {
    mongo.apply().drop.futureValue
  }
}
