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

package uk.gov.hmrc.fileupload.read.infrastructure

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.Matchers
import uk.gov.hmrc.fileupload.StopSystemAfterAll
import uk.gov.hmrc.fileupload.write.infrastructure.EventData
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class ReportActorSpec extends TestKit(ActorSystem("report-actor")) with UnitSpec with Matchers with StopSystemAfterAll {

  "Report Actor" should {
    "shutdown itself after not receiving messages for a specified duration" in {
      val actor = system.actorOf(Props(new TestReportActor {
        override val receiveTimeout = 150.millis
      }))
      val probe = TestProbe()
      probe.watch(actor)
      probe.expectTerminated(actor, 200.millis)
    }
    "handle versions for the read-model" in {
      pending
      "not implemented yet..."
    }
  }

  class TestReportActor extends ReportActor[Unit, Unit] {
    def id: Unit = Unit
    def get: (Unit) => Future[Option[Unit]] = _ => Future.successful(Some((): Unit))
    def save: (Unit) => Future[Boolean] = ???
    def delete: (Unit) => Future[Boolean] = ???
    def defaultState: (Unit) => Unit = _ => ()
    def apply: PartialFunction[(Unit, EventData), Option[Unit]] = ???
  }

}
