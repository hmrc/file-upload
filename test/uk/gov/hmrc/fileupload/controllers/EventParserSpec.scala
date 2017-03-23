/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._
import scala.concurrent.Await

class EventParserSpec extends UnitSpec with ScalaFutures {

  import uk.gov.hmrc.fileupload.Support.StreamImplicits.materializer

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  "event parser" should {

    "should parse a quarantined event" in {
      val request = FakeRequest("POST", "/file-upload/events/fileinquarantinestored")
      val body = """{"envelopeId":"env1","fileId":"file1","fileRefId":"fileRef1","created":0,"name":"test.pdf","contentType":"pdf","fileLength":123,"metadata":{}}""".getBytes

      val accumulator: Accumulator[ByteString, Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = accumulator.run(Source.single(ByteString.fromArray(body))).futureValue

      futureValue shouldBe Right(FileInQuarantineStored(EnvelopeId("env1"), FileId("file1"), FileRefId("fileRef1"), 0, "test.pdf", "pdf", Some(123L), Json.obj()))
    }

    "should parse a no virus detected event" in {
      val request = FakeRequest("POST", "/file-upload/events/filescanned")
      val body = """{"envelopeId":"env1","fileId":"file1","fileRefId":"fileRef1","hasVirus":true}""".getBytes

      val accumulator: Accumulator[ByteString, Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = accumulator.run(Source.single(ByteString.fromArray(body))).futureValue

      futureValue shouldBe Right(FileScanned(EnvelopeId("env1"), FileId("file1"), FileRefId("fileRef1"), hasVirus = true))
    }

    "should respond with a Failure when an unexpected event type is given" in {
      val request = FakeRequest("POST", "/file-upload/events/unexpectedevent")
      val body = """{"envelopeId":"env1","fileId":"file1"}""".getBytes

      val accumulator: Accumulator[ByteString, Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = accumulator.run(Source.single(ByteString.fromArray(body))).futureValue

      val isLeftResult = futureValue match {
        case Left(result) => true
        case _ => false
      }

      isLeftResult shouldBe true
    }
  }

}
