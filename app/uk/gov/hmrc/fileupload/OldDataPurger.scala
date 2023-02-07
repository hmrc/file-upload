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

import play.api.Configuration

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import play.api.Logger
import uk.gov.hmrc.fileupload.write.infrastructure.MongoEventStore
import uk.gov.hmrc.fileupload.read.envelope.Repository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

class OldDataPurger(
  configuration     : Configuration,
  eventStore        : MongoEventStore,
  envelopeRepository: Repository
)(implicit
  ec           : ExecutionContext,
  as           : ActorSystem
) {
  private val logger = Logger(getClass)

  private val purgeEnabled = configuration.get[Boolean]("purge.enabled")
  private val purgeCutoff  = configuration.get[Duration]("purge.cutoff")

  def purge(): Unit =
    (for {
       cutoff <- Future.successful(Instant.now().minusMillis(purgeCutoff.toMillis))
       count  <- eventStore.countOlder(cutoff)
       _      =  logger.info(s"Found $count purgable entries (older than $purgeCutoff i.e. since $cutoff)")
       _      <- if (!purgeEnabled) {
                   logger.info(s"Purge disabled")
                   Future.unit
                 } else if (count > 0) {
                   logger.info(s"Purging old data")
                   val start = System.currentTimeMillis()
                   eventStore.streamOlder(cutoff)
                     .grouped(1000)
                     .mapAsync(parallelism = 1){ streamIds =>
                       for {
                         _ <- envelopeRepository.purge(streamIds.map(id => EnvelopeId(id.toString)))
                         _ <- eventStore.purge(streamIds)
                       } yield streamIds.size
                     }
                     .runWith(Sink.fold(0)(_ + _))
                     .andThen { case count => logger.info(s"Finished purging old envelopes. Cleaned up $count in ${System.currentTimeMillis() - start} ms") }
                 } else
                   Future.unit
     } yield ()
    ).failed
     .foreach {
       case ex => logger.error(s"Failed to purge old data: ${ex.getMessage}", ex)
     }
}
