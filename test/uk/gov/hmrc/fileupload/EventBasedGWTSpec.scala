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

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.hmrc.fileupload.write.infrastructure.{Command, CommandNotAccepted, EventData, Handler}

trait EventBasedGWTSpec[C <: Command, S] extends AnyFeatureSpec with Matchers {

  def handler: Handler[C, S]

  def defaultStatus: S

  case class Given(events: List[EventData])

  case class When(command: C)

  case class Then(result: Either[CommandNotAccepted, List[EventData]])

  def givenWhenThen(`given`: Given, when: When, expected: Then): Unit = {
    info(`given`.toString)
    info(when.toString)
    info(expected.toString)
    val result = handler.handle.apply((when.command, `given`.events.foldLeft(defaultStatus) { (state, event) =>
      handler.on.apply((state, event))
    }))
    result shouldBe expected.result
  }

  def `--`: Given = Given(List())

  import scala.language.implicitConversions

  implicit def eventData2Given(eventData: EventData): Given =
    Given(List(eventData))

  implicit def eventsData2Given(eventsData: List[EventData]): Given =
    Given(eventsData)

  implicit def command2When(command: C): When =
    When(command)

  implicit def eventData2Then(eventData: EventData): Then =
    Then(Right(List(eventData)))

  implicit def eventsData2Then(eventsData: List[EventData]): Then =
    Then(Right(eventsData))

  implicit def commandNotAccepted2Then(e: CommandNotAccepted): Then =
    Then(Left(e))

  implicit class AddEventDataToList(item: EventData) {
    def And(another: EventData) = List(item, another)
  }

  implicit class AddEventDataListToList(items: List[EventData]) {
    def And(another: EventData) = items :+ another
  }
}
