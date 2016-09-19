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

package uk.gov.hmrc.fileupload

import cats.data.Xor
import org.scalatest.{FeatureSpec, Matchers}
import uk.gov.hmrc.fileupload.write.infrastructure.{Command, CommandNotAccepted, EventData, Handler}

trait EventBasedGWTSpec[C <: Command, S, E <: CommandNotAccepted] extends FeatureSpec with Matchers {

  def handler: Handler[C, S, E]

  def defaultStatus: S

  case class Given(events: List[EventData])

  case class When(command: C)

  case class Then(result: Xor[E, List[EventData]])

  def givenWhenThen(given: Given, when: When, expected: Then): Unit = {
    info(given.toString)
    info(when.toString)
    info(expected.toString)
    val result = handler.handle.apply((when.command, given.events.foldLeft(defaultStatus) { (state, event) =>
      handler.on.apply((state, event))
    }))
    result shouldBe expected.result
  }

  def `--`: Given = Given(List())

  import scala.language.implicitConversions

  implicit def EventData2Given(eventData: EventData): Given =
    Given(List(eventData))

  implicit def EventsData2Given(eventsData: List[EventData]): Given =
    Given(eventsData)

  implicit def Command2When(command: C): When =
    When(command)

  implicit def EventData2Then(eventData: EventData): Then =
    Then(Xor.Right(List(eventData)))

  implicit def EventsData2Then(eventsData: List[EventData]): Then =
    Then(Xor.Right(eventsData))

  implicit def CommandNotAccepted2Then(e: E): Then =
    Then(Xor.Left(e))

  implicit class AddEventDataToList(item: EventData) {
    def And(another: EventData) = List(item, another)
  }

  implicit class AddEventDataListToList(items: List[EventData]) {
    def And(another: EventData) = items :+ another
  }

}
