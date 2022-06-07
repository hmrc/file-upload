package uk.gov.hmrc.fileupload.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsError, Json}
import uk.gov.hmrc.fileupload.read.routing.{Algorithm, Audit, Checksum, DownloadUrl, FileTransferFile, FileTransferNotification, Property}

class FileTransferNotificationSpec extends AnyFlatSpec with Matchers {
  implicit val ftnf = FileTransferNotification.format

  "Given correctly formatted JSON, creating a FileTransferNotification" should "return the expected Notification" in {
    val json = Json.parse(
      """{
        "informationType": "UNDEFINED",
        "file":  { "recipientOrSender": "fileUpload",
                   "name": "SomeName",
                   "location": "https://downloadUrl.com",
                   "checksum": { "algorithm": "md5", "value": "4vB/MVHSuPg92a8yDf5IiA==" },
                   "size": 1,
                   "properties": []
                   },
        "audit": { "correlationID": "SomeID" }
      }""")

    json.as[FileTransferNotification] shouldBe FileTransferNotification(
      informationType = "UNDEFINED",
      file            = FileTransferFile(
        recipientOrSender = "fileUpload",
        name              = "SomeName",
        location          = Option(DownloadUrl("https://downloadUrl.com")),
        checksum          = Checksum(Algorithm.Md5, "4vB/MVHSuPg92a8yDf5IiA=="),
        size              = 1,
        properties        = List.empty[Property]
      ),
      audit           = Audit("SomeID")
    )
  }

  "Given correctly formatted JSON with no location, creating a FileTransferNotification" should "return the expected Notification" in {
    val json = Json.parse(
      """{
          "informationType": "UNDEFINED",
          "file":  { "recipientOrSender": "fileUpload",
                     "name": "SomeName",
                     "location": null,
                     "checksum": { "algorithm": "md5", "value": "4vB/MVHSuPg92a8yDf5IiA==" },
                     "size": 1,
                     "properties": []
                     },
          "audit": { "correlationID": "SomeID" }
        }""")

    json.as[FileTransferNotification] shouldBe FileTransferNotification(
      informationType = "UNDEFINED",
      file            = FileTransferFile(
        recipientOrSender = "fileUpload",
        name              = "SomeName",
        location          = None,
        checksum          = Checksum(Algorithm.Md5, "4vB/MVHSuPg92a8yDf5IiA=="),
        size              = 1,
        properties        = List.empty[Property]
      ),
      audit           = Audit("SomeID")
    )
  }

  "Writing a FileTransferNotification object to JSON" should "return the expected JSON result" in {
    val objectAsJson = Json.toJson(FileTransferNotification(
      informationType = "UNDEFINED",
      file            = FileTransferFile(
        recipientOrSender = "fileUpload",
        name              = "SomeName",
        location          = Option(DownloadUrl("https://downloadUrl.com")),
        checksum          = Checksum(Algorithm.Md5, "4vB/MVHSuPg92a8yDf5IiA=="),
        size              = 1,
        properties        = List.empty[Property]
      ),
      audit           = Audit("SomeID")
    ))

    objectAsJson shouldBe Json.parse(
      """{
        "informationType": "UNDEFINED",
        "file":  { "recipientOrSender": "fileUpload",
                   "name": "SomeName",
                   "location": "https://downloadUrl.com",
                   "checksum": { "algorithm": "md5", "value": "4vB/MVHSuPg92a8yDf5IiA==" },
                   "size": 1,
                   "properties": []
                   },
        "audit": { "correlationID": "SomeID" }
      }""")

  }
}