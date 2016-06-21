package uk.gov.hmrc.fileupload.controllers


import play.api.test.PlaySpecification
import play.api.libs.ws._
import play.api.test._

class FileUploadIntegrationSpec extends PlaySpecification{

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

      val response: WSResponse = await(
        support
          .withEnvelope
          .flatMap(_.doUpload(data, filename = "poem.txt"))
      )
      val filename = await(support.refresh.map(_.mayBeEnvelope.get.files.head.head))

      response.status mustEqual OK
      filename mustEqual "poem.txt"
      // TODO check file contents are the same
    }
  }

}
