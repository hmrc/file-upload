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

package uk.gov.hmrc.fileupload.write.infrastructure

import com.codahale.metrics.MetricRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import org.mockito.scalatest.MockitoSugar

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import akka.stream.scaladsl.Sink
import akka.actor.ActorSystem

class MongoMetricRepositorySpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with MockitoSugar
     with DefaultPlayMongoRepositorySupport[UnitOfWork] {

  lazy val metrisMock = mock[MetricRegistry]
  override lazy val repository = new MongoEventStore(mongoComponent, metrisMock)

  private implicit val as = ActorSystem()

  "MongoEventStore.streamOlder" should {
    "only return streamIds where all events are before the cutoff" in {
      val cutoff = Instant.now()
      // all events before the cutoff
      val streamId1 = StreamId(UUID.randomUUID().toString)
      val uow1 = mkUnitOfWork(streamId = Some(streamId1), version = Some(Version(0)), created = Some(Created(cutoff.minusMillis(200).toEpochMilli)))
      val uow2 = mkUnitOfWork(streamId = Some(streamId1), version = Some(Version(1)), created = Some(Created(cutoff.minusMillis(100).toEpochMilli)))

      // straddles the cutoff
      val streamId2 = StreamId(UUID.randomUUID().toString)
      val uow3 = mkUnitOfWork(streamId = Some(streamId2), version = Some(Version(0)), created = Some(Created(cutoff.minusMillis(100).toEpochMilli)))
      val uow4 = mkUnitOfWork(streamId = Some(streamId2), version = Some(Version(1)), created = Some(Created(cutoff.plusMillis(100).toEpochMilli)))

      // all events after the cutoff
      val streamId3 = StreamId(UUID.randomUUID().toString)
      val uow5 = mkUnitOfWork(streamId = Some(streamId3), version = Some(Version(0)), created = Some(Created(cutoff.plusMillis(100).toEpochMilli)))
      val uow6 = mkUnitOfWork(streamId = Some(streamId3), version = Some(Version(1)), created = Some(Created(cutoff.plusMillis(200).toEpochMilli)))
      (for {
         _   <- repository.collection.insertOne(uow1).toFuture()
         _   <- repository.collection.insertOne(uow2).toFuture()
         _   <- repository.collection.insertOne(uow3).toFuture()
         _   <- repository.collection.insertOne(uow4).toFuture()
         _   <- repository.collection.insertOne(uow5).toFuture()
         _   <- repository.collection.insertOne(uow6).toFuture()
         res <- repository.streamOlder(cutoff).runWith(Sink.seq)
       } yield res shouldBe Seq(streamId1)
      ).futureValue
    }
  }

  "MongoEventStore.purge" should {
    "delete the specified envelopes" in {
      val uow1 = mkUnitOfWork()
      val uow2 = mkUnitOfWork(streamId = Some(uow1.streamId), version = Some(uow1.version.nextVersion()))
      val uow3 = mkUnitOfWork()
      val uow4 = mkUnitOfWork()
      (for {
         _   <- repository.collection.insertOne(uow1).toFuture()
         _   <- repository.collection.insertOne(uow2).toFuture()
         _   <- repository.collection.insertOne(uow3).toFuture()
         _   <- repository.collection.insertOne(uow4).toFuture()
         _   <- repository.purge(Seq(uow1.streamId, uow3.streamId))
         res <- repository.collection.find().toFuture()
       } yield res shouldBe Seq(uow4)
      ).futureValue
    }
  }

  def mkUnitOfWork(
    streamId: Option[StreamId] = None,
    version : Option[Version]  = None,
    created : Option[Created]  = None,
    events  : Seq[Event]       = Seq.empty
  ): UnitOfWork =
    UnitOfWork(
      streamId = streamId.getOrElse(StreamId(UUID.randomUUID().toString)),
      version  = version.getOrElse(Version(0)),
      created  = created.getOrElse(Created(Instant.now().toEpochMilli)),
      events   = events
    )
}
