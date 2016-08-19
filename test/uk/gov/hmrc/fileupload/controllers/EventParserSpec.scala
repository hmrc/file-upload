package uk.gov.hmrc.fileupload.controllers

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.events._
import uk.gov.hmrc.play.test.UnitSpec

class EventParserSpec extends UnitSpec with ScalaFutures {
  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  "event parser" should {

    "should parse a quarantined event" in {
      val request = FakeRequest("POST", "/file-upload/events/quarantined")
      val body = """{"envelopeId":"env1","fileId":"file1"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      futureValue shouldBe Right(Quarantined(EnvelopeId("env1"), FileId("file1")))
    }

    "should parse a to transient moved event" in {
      val request = FakeRequest("POST", "/file-upload/events/ToTransientMoved")
      val body = """{"envelopeId":"env1","fileId":"file1"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      futureValue shouldBe Right(ToTransientMoved(EnvelopeId("env1"), FileId("file1")))
    }

    "should parse a moving to transient failed event" in {
      val request = FakeRequest("POST", "/file-upload/events/MovingToTransientFailed")
      val body = """{"envelopeId":"env1","fileId":"file1", "reason": "something not good"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      futureValue shouldBe Right(MovingToTransientFailed(EnvelopeId("env1"), FileId("file1"), "something not good"))
    }

    "should parse a no virus detected event" in {
      val request = FakeRequest("POST", "/file-upload/events/novirusdetected")
      val body = """{"envelopeId":"env1","fileId":"file1"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      futureValue shouldBe Right(NoVirusDetected(EnvelopeId("env1"), FileId("file1")))
    }

    "should parse a virus detected event" in {
      val request = FakeRequest("POST", "/file-upload/events/virusdetected")
      val body = """{"envelopeId":"env1","fileId":"file1", "reason": "something not good"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      futureValue shouldBe Right(VirusDetected(EnvelopeId("env1"), FileId("file1"), "something not good"))
    }

    "should respond with a Failure when an unexpected event type is given" in {
      val request = FakeRequest("POST", "/file-upload/events/unexpectedevent")
      val body = """{"envelopeId":"env1","fileId":"file1"}""".getBytes

      val parserIteratee: Iteratee[Array[Byte], Either[Result, Event]] = EventParser(request)
      val futureValue: Either[Result, Event] = Enumerator(body).run(parserIteratee)

      val isLeftResult = futureValue match {
        case Left(result) => true
        case _ => false
      }

      isLeftResult shouldBe true
    }
  }

}
