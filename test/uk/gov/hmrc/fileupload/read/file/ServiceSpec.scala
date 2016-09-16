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
