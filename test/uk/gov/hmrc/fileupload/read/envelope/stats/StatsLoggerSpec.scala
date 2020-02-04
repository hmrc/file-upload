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

package uk.gov.hmrc.fileupload.read.envelope.stats

import java.time.{LocalDateTime, ZoneId}

import org.mockito.Mockito.{verify, verifyZeroInteractions}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar.{mock => mmock}
import org.scalatest.time.{Milliseconds, Seconds, Span}
import uk.gov.hmrc.fileupload.read.stats._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class StatsLoggerSpec extends MongoSpecSupport with UnitSpec with Eventually with ScalaFutures with BeforeAndAfterAll {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = scaled(Span(100, Milliseconds)))

  override def afterAll {
    mongo.apply().drop.futureValue
  }

  val today = LocalDateTime.now().atZone(ZoneId.of("UTC"))
  val todayAsMilli = today.toInstant.toEpochMilli

  val previousDay = today.minusDays(7)
  val previousDayAsMilli = previousDay.toInstant.toEpochMilli

  val todayFile1 = InProgressFile(FileRefId("ghi"), EnvelopeId(), FileId(), todayAsMilli)
  val todayFile2 = InProgressFile(FileRefId("jkl"), EnvelopeId(), FileId(), todayAsMilli)
  val previousFile1 = InProgressFile(FileRefId("abc"), EnvelopeId(), FileId(), previousDayAsMilli)
  val previousFile2 = InProgressFile(FileRefId("def"), EnvelopeId(), FileId(), previousDayAsMilli)

  "Given an in-progress-files repository, when the logging function is called" should {
    "log the number of in-progress files added today" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 2
      }

      statsLogger.logAddedToday()
      eventually {
        verify(playLogger).logRepoSize(2)
      }
    }

    "not log files added on previous days" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 2
      }

      statsLogger.logAddedToday()
      eventually {
        verify(playLogger).logRepoSize(0)
      }
    }

    "only log files from today" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 4
      }

      statsLogger.logAddedToday()
      eventually {
        verify(playLogger).logRepoSize(2)
      }
    }
  }

  "Given an in-progress-files repository, when the checking function is called" should {
    "log the number of in-progress files added today if it exceeds the maximum" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 2
      }

      statsLogger.checkAddedToday(0)
      eventually {
        verify(playLogger).logRepoWarning(2, 0)
      }
    }

    "not log the number of in-progress files added today if it is less than the maximum" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 2
      }

      statsLogger.checkAddedToday(10)
      eventually {
        verifyZeroInteractions(playLogger)
      }
    }

    "not log files added on previous days" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 2
      }

      statsLogger.checkAddedToday(0)
      eventually {
        verifyZeroInteractions(playLogger)
      }
    }

    "only log files from today" in {
      val repository = Repository(mongo)
      repository.removeAll().futureValue

      val playLogger = mmock[StatsLogWriter]
      val statsLogger = new StatsLogger(repository, playLogger)

      repository.insert(previousFile1)
      repository.insert(previousFile2)
      repository.insert(todayFile1)
      repository.insert(todayFile2)
      eventually {
        Await.result(repository.count, 500.millis) shouldBe 4
      }

      statsLogger.checkAddedToday(0)
      eventually {
        verify(playLogger).logRepoWarning(2, 0)
      }
    }
  }
}
