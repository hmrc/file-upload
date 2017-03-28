package uk.gov.hmrc.fileupload

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.support._
import uk.gov.hmrc.fileupload.write.envelope.{MarkFileAsClean, MarkFileAsInfected, QuarantineFile, StoreFile}

class CallbackIntegrationSpec extends IntegrationSpec with EnvelopeActions with FileActions with EventsActions {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

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

      val response = sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))

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

      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))
      val response = sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))

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

      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))
      val response = sendCommandMarkFileAsInfected(MarkFileAsInfected(envelopeId, fileId, fileRefId))

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

      sendCommandQuarantineFile(QuarantineFile(envelopeId, fileId, fileRefId, 0, "test.pdf", "pdf", Some(123L), Json.obj()))
      sendCommandMarkFileAsClean(MarkFileAsClean(envelopeId, fileId, fileRefId))
      sendCommandStoreFile(StoreFile(envelopeId, fileId, fileRefId, 0))
      val response = upload("test".getBytes, envelopeId, fileId, fileRefId)

      eventually { verifyAvailableCallbackReceived(callbackPath, envelopeId, fileId ) }
    }
  }
}