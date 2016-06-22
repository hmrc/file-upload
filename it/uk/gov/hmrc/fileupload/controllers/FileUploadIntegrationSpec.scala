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

package uk.gov.hmrc.fileupload.controllers

import java.util.UUID

import play.api.libs.ws._
import play.api.test.PlaySpecification

class FileUploadIntegrationSpec extends PlaySpecification{

	val nextId = () => UUID.randomUUID().toString

  val poem =     """
                   |Do strangers make you human
                   |Science fiction visiting bodies as cold fact
                   |What unknown numbers govern our genes or phones
                   |A constant thrum from outer space
                   |Snow makes a sound in sand
                   |You are seen from far far above
                   |Unheard and vanished
                   |bodies dismember to dirt
                   |Hardly alive, hardly a person anymore
                   |Who will I be next and in that life will you know me
                 """.stripMargin

  val data = poem.getBytes
  val support = new FileUploadSupport

  "Application" should{
    "be able to process an upload request" in  {
			val id = nextId()
      val response: WSResponse = await(
        support
          .withEnvelope
          .flatMap(_.doUpload(data, fileId = id))
      )
      val filename = await(support.refresh.map(_.mayBeEnvelope.get.files.head.head))
			val storedPoem = await(support.getFile(id).map(new String(_)))

      response.status mustEqual OK
      filename mustEqual "poem.txt"
	    storedPoem mustEqual poem

    }
  }

}
