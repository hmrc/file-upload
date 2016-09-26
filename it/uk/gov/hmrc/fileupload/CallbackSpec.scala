package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.controllers.{FileInQuarantineStored, FileScanned}
import uk.gov.hmrc.fileupload.read.envelope.Repository
import uk.gov.hmrc.fileupload.support._

class CallbackSpec extends IntegrationSpec with EnvelopeActions with EventsActions with FileActions with Eventually with FakeConsumingService with FakeAuditingService with BeforeAndAfterEach{
  implicit override val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  override implicit lazy val app = FakeApplication(
    additionalConfiguration = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
    ))

  override def beforeEach {
    new Repository(mongo).removeAll().futureValue
  }

  override def afterAll() {
    new Repository(mongo).removeAll().futureValue
  }

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val today = new DateTime().plusMinutes(10)

  feature("Event Callbacks") {

    scenario("When quarantine event is received then the consuming service is notified at the callback specified in the envelope") {

      val callbackPath = "mycallbackpath"
      stubCallback(callbackPath)

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl(callbackPath))))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
      val fileId = FileId("1")
      val fileRefId = FileRefId("1")

      val response = sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))

      response.status shouldBe OK
      eventually { verifyQuarantinedCallbackReceived(callbackPath, envelopeId, fileId ) }
    }

    scenario("When novirusdetected event is received then the consuming service is notified at the callback specified in the envelope") {

      val callbackPath = "mycallbackpath"
      stubCallback(callbackPath)

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl(callbackPath))))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
      val fileId = FileId("1")
      val fileRefId = FileRefId("1")

      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))
      val response = sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))

      response.status shouldBe OK
      eventually { verifyNoVirusDetectedCallbackReceived(callbackPath, envelopeId, fileId ) }
    }

    scenario("When virusdetected event is received then the consuming service is notified at the callback specified in the envelope") {

      val callbackPath = "mycallbackpath"
      stubCallback(callbackPath)

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl(callbackPath))))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
      val fileId = FileId("1")
      val fileRefId = FileRefId("1")

      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))
      val response = sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = true))

      response.status shouldBe OK
      eventually { verifyVirusDetectedCallbackReceived(callbackPath, envelopeId, fileId ) }
    }

    scenario("When stored event is received then the consuming service is notified at the callback specified in the envelope") {

      val callbackPath = "mycallbackpath"
      stubCallback(callbackPath)

      val createEnvelopeResponse = createEnvelope(EnvelopeReportSupport.requestBody(Map("callbackUrl" -> callbackUrl(callbackPath))))
      val locationHeader = createEnvelopeResponse.header("Location").get
      val envelopeId = EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
      val fileId = FileId("1")
      val fileRefId = FileRefId()

      sendFileInQuarantineStored(FileInQuarantineStored(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Json.obj()))
      sendFileScanned(FileScanned(envelopeId, fileId, fileRefId, hasVirus = false))
      val response = upload("test".getBytes, envelopeId, fileId, fileRefId)

      response.status shouldBe OK
      eventually { verifyAvailableCallbackReceived(callbackPath, envelopeId, fileId ) }
    }
  }
}