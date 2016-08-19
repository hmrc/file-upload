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

import java.util.UUID

import akka.actor.ActorRef
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.mvc.{EssentialFilter, RequestHeader, Result}
import play.api.{Application, Configuration, Logger, Play}
import uk.gov.hmrc.fileupload.controllers._
import uk.gov.hmrc.fileupload.envelope.{Service => EnvelopeService}
import uk.gov.hmrc.fileupload.file.{Service => FileService}
import uk.gov.hmrc.fileupload.infrastructure.{DefaultMongoConnection, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal

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

  var subscribe: (ActorRef, Class[_]) => Boolean = _
  var publish: (AnyRef) => Unit = _

  lazy val db = DefaultMongoConnection.db

  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector, appName, Some(t => Logger.warn(t.getMessage, t))) _

  lazy val envelopeRepository = uk.gov.hmrc.fileupload.envelope.Repository.apply(db)

  lazy val updateEnvelope = envelopeRepository.update _
  lazy val getEnvelope = envelopeRepository.get _
  lazy val find = EnvelopeService.find(getEnvelope) _

  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute) _

  lazy val envelopeController = {
    import play.api.libs.concurrent.Execution.Implicits._

    val create = EnvelopeService.create(updateEnvelope) _

    // todo DO WE NEED THIS? mongo can generate ids
    val nextId = () => EnvelopeId(UUID.randomUUID().toString)

    val del = envelopeRepository.delete _
    val delete = EnvelopeService.delete(del, find) _

    new EnvelopeController(createEnvelope = create,
      nextId = nextId,
      findEnvelope = find,
      deleteEnvelope = delete)
  }

  lazy val eventController = {
    new EventController()
  }

  lazy val fileController = {
    import play.api.libs.concurrent.Execution.Implicits._

    val fileRepository = uk.gov.hmrc.fileupload.file.Repository.apply(db)

    val getMetadata = FileService.getMetadata(getEnvelope) _

    val iterateeForUpload = fileRepository.iterateeForUpload _
    val uploadBodyParser = UploadParser.parse(iterateeForUpload) _

    val getFileFromRepo = fileRepository.retrieveFile _
    val retrieveFile = FileService.retrieveFile(getEnvelope, getFileFromRepo) _

    val uploadFile = EnvelopeService.uploadFile(getEnvelope, updateEnvelope) _

    val updateMetadata = EnvelopeService.updateMetadata(getEnvelope, updateEnvelope) _

    new FileController(uploadBodyParser = uploadBodyParser,
      getMetadata = getMetadata,
      retrieveFile = retrieveFile,
      getEnvelope = getEnvelope,
      uploadFile = uploadFile,
      updateMetadata = updateMetadata
    )
  }

  override def onStart(app: Application): Unit = {
    super.onStart(app)

    // event stream
    import play.api.Play.current
    import play.api.libs.concurrent.Akka
    val eventStream = Akka.system.eventStream
    subscribe = eventStream.subscribe
    publish = eventStream.publish

    // notifier
    Akka.system.actorOf(NotifierActor.props(subscribe, find, sendNotification), "notifierActor")

    eventController
    envelopeController
    fileController
  }

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    //TODO: optimise to use pattern match
    if (controllerClass == classOf[EnvelopeController]) {
      envelopeController.asInstanceOf[A]
    } else if (controllerClass == classOf[FileController]) {
      fileController.asInstanceOf[A]
    } else if (controllerClass == classOf[EventController]) {
      eventController.asInstanceOf[A]
    }else {
      super.getControllerInstance(controllerClass)
    }
  }

  override def onBadRequest(request: RequestHeader, error: String): Future[Result] = {
    Future(ExceptionHandler(new BadRequestException(error)))(ExecutionContext.global)
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    Future(ExceptionHandler(ex))(ExecutionContext.global)
  }

  override def microserviceFilters: Seq[EssentialFilter] = defaultMicroserviceFilters // ++ Seq(FileUploadValidationFilter)
}
