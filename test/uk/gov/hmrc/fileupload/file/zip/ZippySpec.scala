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

package uk.gov.hmrc.fileupload.file.zip

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.fileupload.envelope.Envelope
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.fileupload.file.zip.Utils.Bytes
import uk.gov.hmrc.fileupload.file.zip.Zippy.{EmptyEnvelopeError, EnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Support}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ZippySpec extends UnitSpec with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  "Zippy" should {
    "provide a zip file containing an envelope including its files" in {
      val envelope = Support.envelopeWithAFile(FileId("myfile"))
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelope))
      val retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_, _) => Xor.right(
        FileFound(name = Some("fileTest"), length = 100, data = Enumerator("one".getBytes(), "two".getBytes(), "three".getBytes()))
      )

      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFile)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isRight shouldBe true
      zipResult.map(zipStream => {
        val eventualBytes: Future[List[Bytes]] = zipStream.run(Iteratee.getChunks[Bytes])
        eventualBytes.futureValue.size shouldNot be(0)
      })
    }

    "fail when no envelope is found" in {
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.left(FindEnvelopeNotFoundError))
      val retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_, _) => Xor.right(
        FileFound(name = Some("fileTest"), length = 100, data = Enumerator("one".getBytes(), "two".getBytes(), "three".getBytes()))
      )

      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFile)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap {
        case EnvelopeNotFoundError =>
        case _ => fail("EnvelopeNotFound was expected")
      }
    }

    "fail when no file is found in the envelope" in {
      val envelopeWithNoFiles = Support.envelope
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelopeWithNoFiles))
      val retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_, _) => Xor.right(
        FileFound(name = Some("fileTest"), length = 100, data = Enumerator("one".getBytes(), "two".getBytes(), "three".getBytes()))
      )

      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFile)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap {
        case EmptyEnvelopeError =>
        case _ => fail("EmptyEnvelopeError was expected")
      }
    }

    "fail when a file cannot be retrieved" ignore {
      val envelope = Support.envelopeWithAFile(FileId("myfile"))
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelope))
      val retrieveFile: (Envelope, FileId) => Future[GetFileResult] = (_, _) => Xor.left(GetFileNotFoundError)

      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFile)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap {
        case EmptyEnvelopeError =>
        case _ => fail("EmptyEnvelopeError was expected")
      }
    }
  }

}
