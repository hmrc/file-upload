package uk.gov.hmrc.fileupload

import org.scalatest.BeforeAndAfterEach
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import play.api.libs.ws._
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.controllers.FileInQuarantineStored
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, EventsActions, FileActions, IntegrationSpec}

/**
  * Integration tests for FILE-180
  * Delete File
  *
  */
class DeleteFileIntegrationSpec extends IntegrationSpec with FileActions with EnvelopeActions with EventsActions with BeforeAndAfterEach{

  override implicit lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
    ))

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  override def afterEach {
    mongo.apply().drop().futureValue
  }

  implicit override val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(5, Millis))

  feature("Delete file") {

    scenario("Delete an existing file") {
      Given("I have a valid envelope-id")
      val envelopeId = createEnvelope()

      And("I have a valid file-id")
      val fileId = FileId(s"fileId-${nextId()}")

      And("I have a valid file-ref-id")
      val fileRefId = FileRefId(s"fileRefId-${nextId()}")

      And("FileInQuarantineStored")
      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))

      And("I uploaded a file")
      upload("abc".getBytes(), envelopeId, fileId, fileRefId)

      When(s"I invoke DELETE envelope/$envelopeId/files/$fileId")
      val response: WSResponse = delete(envelopeId, fileId)

      Then("I will receive a 200 OK response")
      response.status shouldBe OK
    }
  }
}
