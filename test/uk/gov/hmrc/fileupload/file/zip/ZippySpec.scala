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

package uk.gov.hmrc.fileupload.file.zip

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.EitherValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Support}
import uk.gov.hmrc.fileupload.file.zip.Zippy._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatusClosed, EnvelopeStatusOpen}
import uk.gov.hmrc.fileupload.read.envelope.Service._

import scala.concurrent.Future

class ZippySpec
  extends AnyWordSpecLike
     with Matchers
     with ScalaFutures
     with IntegrationPatience
     with EitherValues {

  import uk.gov.hmrc.fileupload.Support.StreamImplicits.system
  import scala.concurrent.ExecutionContext.Implicits.global

  private val downloadZip: Envelope => Future[Source[ByteString, NotUsed]] =
    _ => Future.successful(Source.fromIterator(() => List(ByteString("one"), ByteString("two")).toIterator))

  "Zippy" should {
    "provide a zip file containing an envelope including its files in S3" in {
      val envelope = Support.envelopeWithAFile(FileId("myfile")).copy(status = EnvelopeStatusClosed)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Right(envelope))
      val zipResult = Zippy.zipEnvelope(getEnvelope, downloadZip)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.right.value.runFold(ByteString.empty)(_ concat _).futureValue.size shouldNot be(0)
    }

    "fail if envelope is not in Closed status" in {
      val envelope = Support.envelopeWithAFile(FileId("myfile")).copy(status = EnvelopeStatusOpen)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Right(envelope))
      val zipResult = Zippy.zipEnvelope(getEnvelope, downloadZip)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.left.map {
        case EnvelopeNotRoutedYet =>
        case _                    => fail("EnvelopeNotRoutedYet was expected")
      }
    }

    "fail when no envelope is found" in {
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Left(FindEnvelopeNotFoundError))
      val zipResult = Zippy.zipEnvelope(getEnvelope, downloadZip)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.left.value match {
        case ZipEnvelopeNotFoundError =>
        case _                        => fail("EnvelopeNotFound was expected")
      }
    }

    "fail when no service error" in {
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Left(FindServiceError("A service error")))
      val zipResult = Zippy.zipEnvelope(getEnvelope, downloadZip)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.left.value match {
        case ZipProcessingError("A service error") =>
        case _                                     => fail("EnvelopeNotFound was expected")
      }
    }

    "return empty zip when no file is found in the envelope" in {
      val envelopeWithNoFiles = Support.envelope.copy(status = EnvelopeStatusClosed)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Right(envelopeWithNoFiles))
      val zipResult = Zippy.zipEnvelope(getEnvelope, downloadZip)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.right.value.runFold(ByteString.empty)(_ concat _).futureValue.size shouldBe 0
    }
  }
}
