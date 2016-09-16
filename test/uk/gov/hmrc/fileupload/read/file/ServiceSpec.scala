package uk.gov.hmrc.fileupload.read.file

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.{FileId, FileRefId, Support}
import uk.gov.hmrc.fileupload.read.envelope.{File, FileStatusQuarantined}
import uk.gov.hmrc.fileupload.read.file.Service._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "retrieving a file" should {
    "succeed if file was available" in {
      val fileId = FileId()
      val expectedFileName = Some("expected-file-name.txt")
      val envelope = Support.envelope.copy(
        files = Some(List(File(fileId, fileRefId = FileRefId("ref"), FileStatusQuarantined, name = expectedFileName)))
      )
      val length = 10
      val data = Enumerator("sth".getBytes())

      val result = Service.retrieveFile(
        getFileFromRepo = _ => Future.successful(Some(FileData(length, data)))
      )(envelope, fileId).futureValue

      result.isRight shouldBe true
      result.foreach { fileFound =>
        fileFound shouldBe FileFound(expectedFileName, length, data)
      }
    }
    "fail if file was not available" in {
      val fileId = FileId()
      val envelope = Support.envelopeWithAFile(fileId)

      val result = Service.retrieveFile(
        getFileFromRepo = _ => Future.successful(None)
      )(envelope, fileId).futureValue

      result shouldBe Xor.Left(GetFileNotFoundError)
    }
  }

}
