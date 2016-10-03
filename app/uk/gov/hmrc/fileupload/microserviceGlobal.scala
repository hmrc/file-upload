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
import uk.gov.hmrc.fileupload.controllers.routing.RoutingController
import uk.gov.hmrc.fileupload.controllers.transfer.TransferController
import uk.gov.hmrc.fileupload.file.zip.Zippy
import uk.gov.hmrc.fileupload.infrastructure.{DefaultMongoConnection, PlayHttp}
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, WithValidEnvelope, Service => EnvelopeService, _}
import uk.gov.hmrc.fileupload.read.file.{Service => FileService}
import uk.gov.hmrc.fileupload.read.infrastructure.CoordinatorActor
import uk.gov.hmrc.fileupload.read.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.stats.{Stats, StatsActor}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.utils.Contexts
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, MongoEventStore}
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

  lazy val envelopeRepository = uk.gov.hmrc.fileupload.read.envelope.Repository.apply(db)

  lazy val getEnvelope = envelopeRepository.get _

  lazy val withValidEnvelope = new WithValidEnvelope(getEnvelope)

  lazy val find = EnvelopeService.find(getEnvelope) _
  lazy val findMetadata = EnvelopeService.findMetadata(find) _

  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute) _

  lazy val fileRepository = uk.gov.hmrc.fileupload.read.file.Repository.apply(db)
  val iterateeForUpload = fileRepository.iterateeForUpload _
  val getFileFromRepo = fileRepository.retrieveFile _
  lazy val retrieveFile = FileService.retrieveFile(getFileFromRepo) _
  lazy val findAllInProgressFile = Stats.all _

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  implicit lazy val eventStore = new MongoEventStore(db)

  lazy val envelopeCommandHandler = {
    (command: EnvelopeCommand) =>
      Aggregate[EnvelopeCommand, write.envelope.Envelope](
        handler = write.envelope.Envelope,
        defaultState = () => write.envelope.Envelope(),
        publish = publish).handleCommand(command)
  }

  lazy val envelopeController = {
    val nextId = () => EnvelopeId(UUID.randomUUID().toString)
    new EnvelopeController(
      nextId = nextId,
      handleCommand = envelopeCommandHandler,
      findEnvelope = find,
      findMetadata = findMetadata,
      findAllInProgressFile = findAllInProgressFile)
  }

  lazy val eventController = {
    new EventController(envelopeCommandHandler, eventStore.unitsOfWorkForAggregate)
  }

  lazy val fileController = {
    import play.api.libs.concurrent.Execution.Implicits._
    val uploadBodyParser = UploadParser.parse(iterateeForUpload) _
    new FileController(uploadBodyParser = uploadBodyParser,
      retrieveFile = retrieveFile,
      withValidEnvelope = withValidEnvelope,
      handleCommand = envelopeCommandHandler)
  }

  lazy val transferController = {
    val getEnvelopesByDestination = envelopeRepository.getByDestination _
    val zipEnvelope = Zippy.zipEnvelope(find, retrieveFile) _
    new TransferController(getEnvelopesByDestination, envelopeCommandHandler, zipEnvelope)
  }

  lazy val testOnlyController = {
    val fileRepository = uk.gov.hmrc.fileupload.read.file.Repository.apply(db)
    val envelopeRepository = uk.gov.hmrc.fileupload.read.envelope.Repository.apply(db)
    val allEnvelopes = envelopeRepository.all _
    new TestOnlyController(fileRepository, envelopeRepository, allEnvelopes)
  }

  lazy val routingController = {
    new RoutingController(envelopeCommandHandler)
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
    Akka.system.actorOf(StatsActor.props(subscribe, find, sendNotification), "statsActor")

    // envelope read model
    val createReportActor = EnvelopeReportActor.props(
      envelopeRepository.get(_)(Contexts.blockingDb),
      envelopeRepository.update(_)(Contexts.blockingDb),
      envelopeRepository.delete(_)(Contexts.blockingDb),
      defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id)) _

    Akka.system.actorOf(CoordinatorActor.props(
      createReportActor, Set(classOf[EnvelopeEvent]), subscribe), "envelopeReadModelCoordinator")

    eventController
    envelopeController
    fileController
    transferController
    testOnlyController
    routingController
  }

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    //TODO: optimise to use pattern match
    if (controllerClass == classOf[EnvelopeController]) {
      envelopeController.asInstanceOf[A]
    } else if (controllerClass == classOf[FileController]) {
      fileController.asInstanceOf[A]
    } else if (controllerClass == classOf[EventController]) {
      eventController.asInstanceOf[A]
    } else if (controllerClass == classOf[TransferController]) {
      transferController.asInstanceOf[A]
    } else if (controllerClass == classOf[TestOnlyController]) {
      testOnlyController.asInstanceOf[A]
    } else if (controllerClass == classOf[RoutingController]) {
      routingController.asInstanceOf[A]
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

  override def microserviceFilters: Seq[EssentialFilter] = defaultMicroserviceFilters // ++ Seq(FileUploadValidationFilter)
}
