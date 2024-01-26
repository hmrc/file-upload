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

package uk.gov.hmrc.fileupload

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.fileupload.write.infrastructure.{MongoEventStore, StreamId}
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.mongo.lock.{Lock, LockRepository}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

class OldDataPurgerSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with MockitoSugar {

  import ExecutionContext.Implicits.global
  implicit val as = ActorSystem()

  trait Setup {
    lazy val configuration =
      Configuration(
        "purge.enabled" -> true,
        "purge.cutoff" -> "10.days"
      )

    lazy val mockEventStore         = mock[MongoEventStore]
    lazy val mockEnvelopeRepository = mock[Repository]
    lazy val mockLockRepository     = mock[LockRepository]

    when(mockLockRepository.takeLock(any, any, any))
      .thenReturn(Future.successful(Some(mock[Lock])))

    when(mockLockRepository.releaseLock(any, any))
      .thenReturn(Future.unit)

    lazy val now = {
      val now = Instant.now()
      () => now
    }

    lazy val oldDataPurger =
      new OldDataPurger(configuration, mockEventStore, mockEnvelopeRepository, mockLockRepository, now)
  }

  "OldDataPurger.purge" should {
    "not purge if disabled" in new Setup {
      override lazy val configuration =
        Configuration(
          "purge.enabled" -> false,
          "purge.cutoff" -> "10.days"
        )

      oldDataPurger.purge().futureValue

      verify(mockEnvelopeRepository, never).purge(any)
      verify(mockEventStore, never).purge(any)
    }

    "purge if data to purge is found" in new Setup {
      val envelopeIds = Seq("e1", "e2", "e3", "e4")

      when(mockEventStore.streamOlder(any))
        .thenReturn(Source.fromIterator(() => envelopeIds.map(StreamId.apply).toIterator))

      when(mockEnvelopeRepository.purge(any))
        .thenReturn(Future.unit)

      when(mockEventStore.purge(any))
        .thenReturn(Future.unit)

      oldDataPurger.purge().futureValue

      verify(mockEventStore).streamOlder(now().minusMillis(10.days.toMillis))
      verify(mockEnvelopeRepository).purge(envelopeIds.map(EnvelopeId.apply))
      verify(mockEventStore).purge(envelopeIds.map(StreamId.apply))
    }
  }
}
