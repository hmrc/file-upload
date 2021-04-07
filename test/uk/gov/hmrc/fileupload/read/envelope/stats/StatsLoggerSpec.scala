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

package uk.gov.hmrc.fileupload.read.envelope.stats

import java.time.{LocalDateTime, ZoneId}
import org.mockito.MockitoSugar
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.read.stats._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, MongoSupport}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class StatsLoggerSpec
  extends MongoSupport
    with AnyWordSpecLike
    with CleanMongoCollectionSupport
    with Matchers
    with MockitoSugar
    with Eventually
    with IntegrationPatience {

  val today = LocalDateTime.now().atZone(ZoneId.of("UTC"))
  val todayAsMilli = today.toInstant.toEpochMilli

  val previousDay = today.minusDays(7)
  val previousDayAsMilli = previousDay.toInstant.toEpochMilli

  val todayFile1 = InProgressFile(FileRefId("ghi"), EnvelopeId(), FileId(), todayAsMilli)
  val todayFile2 = InProgressFile(FileRefId("jkl"), EnvelopeId(), FileId(), todayAsMilli)
  val previousFile1 = InProgressFile(FileRefId("abc"), EnvelopeId(), FileId(), previousDayAsMilli)
  val previousFile2 = InProgressFile(FileRefId("def"), EnvelopeId(), FileId(), previousDayAsMilli)

  val oneDayDuration = 1.day

  "Given an in-progress-files repository, when the logging function is called" should {
    "log the number of in-progress files added over a time period as warning if it exceeds the maximum" in {
      val repository = Repository(mongoComponent)

      val playLogger = mock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        repository.all().futureValue.size shouldBe 2
      }

      statsLogger.logAddedOverTimePeriod(oneDayDuration, Some(0))
      eventually {
        verify(playLogger).logRepoWarning(2, 0, oneDayDuration)
      }
    }

    "log the number of in-progress files added today as info if it is less than the maximum" in {
      val repository = Repository(mongoComponent)

      val playLogger = mock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        repository.all().futureValue.size shouldBe 2
      }

      statsLogger.logAddedOverTimePeriod(oneDayDuration, Some(10))
      eventually {
        verify(playLogger).logRepoSize(2, oneDayDuration)
      }
    }

    "not count files added on previous days in logs" in {
      val repository = Repository(mongoComponent)

      val playLogger = mock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      eventually {
        repository.all().futureValue.size shouldBe 2
      }

      statsLogger.logAddedOverTimePeriod(oneDayDuration, Some(0))
      eventually {
        verify(playLogger).logRepoSize(0, oneDayDuration)
      }
    }

    "only count files from today in logs" in {
      val repository = Repository(mongoComponent)

      val playLogger = mock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        repository.all().futureValue.size shouldBe 4
      }

      statsLogger.logAddedOverTimePeriod(oneDayDuration, Some(0))
      eventually {
        verify(playLogger).logRepoWarning(2, 0, oneDayDuration)
      }
    }
  }
}
