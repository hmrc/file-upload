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

package uk.gov.hmrc.fileupload.file.zip

import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.ZipInputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.fileupload.file.zip.Utils.Bytes
import uk.gov.hmrc.fileupload.file.zip.Zippy._
import uk.gov.hmrc.fileupload.read.envelope.Service._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatusClosed, EnvelopeStatusOpen}
import uk.gov.hmrc.fileupload.read.stats.Stats.FileFound
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId, Support}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ZippySpec extends UnitSpec with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  val retrieveFileFormS3: (EnvelopeId, FileId) => Future[Source[ByteString, _]] = (_, _) =>
    Future.successful(Source.fromIterator(() => List(ByteString("one"), ByteString("two")).toIterator))

  val retrieveFileFormMongoDB: (Envelope, FileId) => Future[GetFileResult] = (_, _) => Xor.right(
    FileFound(name = Some("fileTest"), length = 100, data = Enumerator("one".getBytes(), "two".getBytes(), "three".getBytes()))
  )

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(20, Millis))

  "Zippy" should {
    "provide a zip file containing an envelope including its files in S3" in {
      val envelope = Support.envelopeWithAFile(FileId("myfile")).copy(status = EnvelopeStatusClosed)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelope))
      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFileFormS3, retrieveFileFormMongoDB)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isRight shouldBe true
      zipResult.map(zipStream => {
        val eventualBytes: Future[List[Bytes]] = zipStream.run(Iteratee.getChunks[Bytes])
        eventualBytes.futureValue.size shouldNot be(0)
      })
    }

    "fail if envelope is not in Closed status" in {
      val envelope = Support.envelopeWithAFile(FileId("myfile")).copy(status = EnvelopeStatusOpen)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelope))
      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFileFormS3, retrieveFileFormMongoDB)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap{
        case EnvelopeNotRoutedYet =>
        case _ => fail("EnvelopeNotRoutedYet was expected")
      }
    }

    "fail when no envelope is found" in {
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.left(FindEnvelopeNotFoundError))
      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFileFormS3, retrieveFileFormMongoDB)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap {
        case ZipEnvelopeNotFoundError =>
        case _ => fail("EnvelopeNotFound was expected")
      }
    }

    "fail when no service error" in {
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.left(FindServiceError("A service error")))
      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFileFormS3, retrieveFileFormMongoDB)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isLeft shouldBe true
      zipResult.leftMap {
        case ZipProcessingError("A service error") =>
        case _ => fail("EnvelopeNotFound was expected")
      }
    }

    "return empty zip when no file is found in the envelope" in {
      val envelopeWithNoFiles = Support.envelope.copy(status = EnvelopeStatusClosed)
      val getEnvelope: (EnvelopeId) => Future[FindResult] = _ => Future.successful(Xor.right(envelopeWithNoFiles))
      val zipResult = Zippy.zipEnvelope(getEnvelope, retrieveFileFormS3, retrieveFileFormMongoDB)(envelopeId = EnvelopeId("myid")).futureValue

      zipResult.isRight shouldBe true
      zipResult.map { zipStream =>
        val bytes: Array[Byte] = (zipStream |>>> Iteratee.consume[Array[Byte]]()).futureValue
        val zip = new ZipInputStream(new ByteArrayInputStream(bytes))
        zip.getNextEntry shouldBe null
      }
    }

    "return Boolean when run checkIsTheFileInS3" in {
      Zippy.checkIsTheFileInS3(FileRefId(UUID.randomUUID().toString)) shouldBe false
      Zippy.checkIsTheFileInS3(FileRefId("067535a4-26e1-48cc-acc9-e4389ea7d683")) shouldBe false
      Zippy.checkIsTheFileInS3(FileRefId("524f1de1-8472-4638-9b2a-f1d51943a0f2")) shouldBe false
      Zippy.checkIsTheFileInS3(FileRefId("")) shouldBe true
      Zippy.checkIsTheFileInS3(FileRefId("GDaUeyIiOYoFALm.fMwt4NBMEAAn3diu")) shouldBe true
      Zippy.checkIsTheFileInS3(FileRefId("DL6n5okV0ZmBJKO.UomyZq2K7GH1OoXU")) shouldBe true
      Zippy.checkIsTheFileInS3(FileRefId("Pt9fzI75VlHcEM5k_6ZmJzkE8YeG2jxX")) shouldBe true
      Zippy.checkIsTheFileInS3(FileRefId("5R0IVheqf9dxMNfWz9j13yT9ZHYSbCvx")) shouldBe true
    }

  }

}
