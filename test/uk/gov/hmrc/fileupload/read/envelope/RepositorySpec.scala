/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.envelope

import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class RepositorySpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with DefaultPlayMongoRepositorySupport[Envelope] {

  override val repository: Repository =
    Repository(mongoComponent)

  "Repository.purge" should {
    "delete the specified envelopes" in {
      val envelope1 = Envelope()
      val envelope2 = Envelope()
      val envelope3 = Envelope()
      (for {
         _ <- repository.collection.insertOne(envelope1).toFuture()
         _ <- repository.collection.insertOne(envelope2).toFuture()
         _ <- repository.collection.insertOne(envelope3).toFuture()
         _ <- repository.purge(Seq(envelope1._id, envelope2._id))
         res <- repository.collection.find().toFuture()
       } yield res shouldBe Seq(envelope3)
      ).futureValue
    }
  }
}
