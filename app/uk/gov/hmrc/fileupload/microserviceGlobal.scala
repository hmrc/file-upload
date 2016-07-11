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

package uk.gov.hmrc.fileupload

import com.typesafe.config.Config
import play.api.mvc.{EssentialFilter, RequestHeader, Result}
import play.api.{Application, Configuration, Play}
import uk.gov.hmrc.fileupload.controllers._
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import net.ceedubs.ficus.Ficus._
import uk.gov.hmrc.fileupload.envelope.Envelope
import uk.gov.hmrc.fileupload.infrastructure.DefaultMongoConnection
import uk.gov.hmrc.play.http.BadRequestException

import scala.concurrent.{ExecutionContext, Future}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector
  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode {

  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None

  import play.api.libs.concurrent.Execution.Implicits._

  lazy val db = DefaultMongoConnection.db

  lazy val envelopeRepository = uk.gov.hmrc.fileupload.envelope.Repository.apply(db)

  lazy val update = envelopeRepository.update _
  lazy val get = envelopeRepository.get _
  lazy val find = uk.gov.hmrc.fileupload.envelope.Service.find(get) _

  lazy val addFileToEnvelope = uk.gov.hmrc.fileupload.envelope.Service.addFile(
    (envelopeId: String, fileId: String) => routes.FileController.upload(envelopeId = envelopeId, fileId = fileId).url, update, find) _

  lazy val envelopeController = {
    import uk.gov.hmrc.fileupload.envelope.Service
    import play.api.libs.concurrent.Execution.Implicits._

    val create = Service.create(update) _
    val toEnvelope = EnvelopeReport.fromEnvelopeReportOption _

    val del = envelopeRepository.delete _
    val delete = Service.delete(del, find) _

    val seal = Service.seal(update, find) _

    new EnvelopeController(createEnvelope = create, toEnvelope = toEnvelope, findEnvelope = find, deleteEnvelope = delete, sealEnvelope = seal)
  }

  lazy val fileController = {
    import uk.gov.hmrc.fileupload.file.Service
    import play.api.libs.concurrent.Execution.Implicits._

    val fileRepository = uk.gov.hmrc.fileupload.file.Repository.apply(db)

    val getFileMetadata = fileRepository.getFileMetadata _
    val getMetadata = Service.getMetadata(getFileMetadata) _

    val addFileMetadata = fileRepository.addFileMetadata _
    val updateMetadata = Service.updateMetadata(addFileMetadata) _

    val iterateeForUpload = fileRepository.iterateeForUpload _
    val uploadBodyParser = UploadParser.parse(iterateeForUpload) _

    val fromRepository = fileRepository.retrieveFile _
    val retrieveFile = Service.retrieveFile(fromRepository) _

    new FileController(uploadBodyParser = uploadBodyParser, addFileToEnvelope = addFileToEnvelope, getMetadata = getMetadata, updateMetadata = updateMetadata, retrieveFile = retrieveFile)
  }

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    //TODO: optimise to use pattern match
    if (controllerClass == classOf[EnvelopeController]) {
      envelopeController.asInstanceOf[A]
    } else if (controllerClass == classOf[FileController]) {
      fileController.asInstanceOf[A]
    } else {
      super.getControllerInstance(controllerClass)
    }
  }

	override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
		Future(ExceptionHandler(new BadRequestException(error)))(ExecutionContext.global)
	}

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    Future(ExceptionHandler(ex))(ExecutionContext.global)
  }

	override def microserviceFilters: Seq[EssentialFilter] = defaultMicroserviceFilters  // ++ Seq(FileUploadValidationFilter)
}
