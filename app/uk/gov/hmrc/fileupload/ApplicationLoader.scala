/*
 * Copyright 2017 HM Revenue & Customs
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
import javax.inject.Provider

import akka.actor.ActorRef
import com.kenshoo.play.metrics.{MetricsController, MetricsFilterImpl}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.ApplicationLoader.Context
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSRequest
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import reactivemongo.api.commands
import uk.gov.hmrc.fileupload.admin.{Routes => AdminRoutes}
import uk.gov.hmrc.fileupload.app.{Routes => AppRoutes}
import uk.gov.hmrc.fileupload.controllers.routing.RoutingController
import uk.gov.hmrc.fileupload.controllers.transfer.TransferController
import uk.gov.hmrc.fileupload.controllers.{AdminController, _}
import uk.gov.hmrc.fileupload.file.zip.Zippy
import uk.gov.hmrc.fileupload.infrastructure._
import uk.gov.hmrc.fileupload.manualdihealth.{Routes => HealthRoutes}
import uk.gov.hmrc.fileupload.prod.Routes
import uk.gov.hmrc.fileupload.read.envelope.{WithValidEnvelope, Service => EnvelopeService, _}
import uk.gov.hmrc.fileupload.read.file.{Service => FileService}
import uk.gov.hmrc.fileupload.read.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.read.stats.{Stats, StatsActor}
import uk.gov.hmrc.fileupload.routing.{Routes => RoutingRoutes}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.{Routes => TransferRoutes}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, MongoEventStore, StreamId}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.{NoCacheFilter, RecoveryFilter}
import uk.gov.hmrc.play.graphite.GraphiteMetricsImpl
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter


class ApplicationLoader extends play.api.ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    new ApplicationModule(context).application
  }
}

class ApplicationModule(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents with AppName {

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  val subscribe: (ActorRef, Class[_]) => Boolean = actorSystem.eventStream.subscribe
  val publish: (AnyRef) => Unit = actorSystem.eventStream.publish
  val withBasicAuth: BasicAuth = BasicAuth(basicAuthConfiguration(configuration))
  val envelopeDefaultConstraints = envelopeConstraintsConfiguration(configuration)
  val envelopeHandler = new EnvelopeHandler(envelopeDefaultConstraints)

  val eventStore = if (environment.mode == Mode.Prod && configuration.getBoolean("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    new MongoEventStore(db, writeConcern = commands.WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true))
  } else {
    new MongoEventStore(db)
  }

  val updateEnvelope = if (environment.mode == Mode.Prod && configuration.getBoolean("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    envelopeRepository.update(writeConcern = commands.WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true)) _
  } else {
    envelopeRepository.update() _
  }

  def envelopeConstraintsConfiguration(config: Configuration) = {

    def throwException(key: String) = {
      throw new IllegalStateException(s"default value $key need to define")
    }

    DefaultEnvelopeConstraints(
      config.getInt("envelopeDefaultConstraints.maxNumFiles").getOrElse(throwException("maxNumFiles")),
      config.getString("envelopeDefaultConstraints.maxSize").getOrElse(throwException("maxSize")),
      config.getString("envelopeDefaultConstraints.maxSizePerItem").getOrElse(throwException("maxSizePerItem"))
    )
  }

  def basicAuthConfiguration(config: Configuration): BasicAuthConfiguration = {
    def getUsers(config: Configuration): List[User] = {
      config.getString("basicAuth.authorizedUsers").map { s =>
        s.split(";").flatMap(
          user => {
            user.split(":") match {
              case Array(username, password) => Some(User(username, password))
              case _ => None
            }
          }
        ).toList
      }.getOrElse(List.empty)
    }

    config.getBoolean("feature.basicAuthEnabled").getOrElse(false) match {
      case true => BasicAuthEnabled(getUsers(config))
      case false => BasicAuthDisabled
    }
  }

  lazy val db = new ReactiveMongoComponentImpl(application, applicationLifecycle).mongoConnector.db

  // notifier
  actorSystem.actorOf(NotifierActor.props(subscribe, find, sendNotification), "notifierActor")
  actorSystem.actorOf(StatsActor.props(subscribe, find, sendNotification, saveFileQuarantinedStat,
    deleteVirusDetectedStat, deleteFileStoredStat), "statsActor")

  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    metricsFilter,
    microserviceAuditFilter,
    loggingFilter,
    NoCacheFilter,
    RecoveryFilter
  )

  override lazy val httpErrorHandler = new GlobalErrorHandler


  lazy val auditedHttpExecute = PlayHttp.execute(MicroserviceAuditFilter.auditConnector,
    appName, Some(t => Logger.warn(t.getMessage, t))) _

  lazy val envelopeRepository = uk.gov.hmrc.fileupload.read.envelope.Repository.apply(db)

  lazy val getEnvelope = envelopeRepository.get _

  lazy val withValidEnvelope = new WithValidEnvelope(getEnvelope)

  lazy val find = EnvelopeService.find(getEnvelope) _
  lazy val findMetadata = EnvelopeService.findMetadata(find) _

  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute, wsClient) _

  lazy val fileRepository = uk.gov.hmrc.fileupload.read.file.Repository.apply(db)
  val iterateeForUpload = fileRepository.iterateeForUpload _
  val getFileFromRepo = fileRepository.retrieveFile _
  lazy val retrieveFile = FileService.retrieveFile(getFileFromRepo) _
  lazy val retrieveFileMetaData = fileRepository.retrieveFileMetaData _
  lazy val fileChunksInfo = fileRepository.chunksCount _

  lazy val statsRepository = uk.gov.hmrc.fileupload.read.stats.Repository.apply(db)
  lazy val saveFileQuarantinedStat = Stats.save(statsRepository.insert) _
  lazy val deleteFileStoredStat = Stats.deleteFileStored(statsRepository.delete) _
  lazy val deleteVirusDetectedStat = Stats.deleteVirusDetected(statsRepository.delete) _
  lazy val allInProgressFile = Stats.all(statsRepository.all) _

  // envelope read model
  lazy val createReportHandler = new EnvelopeReportHandler(
    toId = (streamId: StreamId) => EnvelopeId(streamId.value),
    updateEnvelope,
    envelopeRepository.delete,
    defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id))

  // command handler
  lazy val envelopeCommandHandler = {
    (command: EnvelopeCommand) =>
      new Aggregate[EnvelopeCommand, write.envelope.Envelope](
        handler = envelopeHandler,
        defaultState = () => write.envelope.Envelope(),
        publish = publish,
        publishAllEvents = createReportHandler.handle(replay = false))(eventStore, defaultContext).handleCommand(command)
  }

  lazy val envelopeController = {
    val nextId = () => EnvelopeId(UUID.randomUUID().toString)
    val getEnvelopesByStatus = envelopeRepository.getByStatus _
    new EnvelopeController(
      withBasicAuth = withBasicAuth,
      envelopeDefaultConstraints = envelopeDefaultConstraints,
      nextId = nextId,
      handleCommand = envelopeCommandHandler,
      findEnvelope = find,
      findMetadata = findMetadata,
      findAllInProgressFile = allInProgressFile,
      deleteInProgressFile = statsRepository.deleteByFileRefId,
      getEnvelopesByStatus = getEnvelopesByStatus)
  }

  lazy val eventController = {
    new EventController(envelopeCommandHandler, eventStore.unitsOfWorkForAggregate, createReportHandler.handle(replay = true))
  }

  lazy val commandController = {
    new CommandController(envelopeCommandHandler)
  }

  lazy val fileController = {
    import play.api.libs.concurrent.Execution.Implicits._
    val uploadBodyParser = UploadParser.parse(iterateeForUpload) _
    new FileController(
      withBasicAuth = withBasicAuth,
      uploadBodyParser = uploadBodyParser,
      retrieveFile = retrieveFile,
      withValidEnvelope = withValidEnvelope,
      handleCommand = envelopeCommandHandler)
  }

  lazy val adminController = {
    new AdminController(getFileInfo = retrieveFileMetaData,
      getChunks = fileChunksInfo)
  }

  lazy val transferController = {
    val getEnvelopesByDestination = envelopeRepository.getByDestination _
    val zipEnvelope = Zippy.zipEnvelope(find, retrieveFile) _
    new TransferController(withBasicAuth, getEnvelopesByDestination, envelopeCommandHandler, zipEnvelope)
  }

  lazy val testOnlyController = {
    new TestOnlyController(recreateCollections = List(eventStore.recreate, envelopeRepository.recreate, fileRepository.recreate, statsRepository.recreate))
  }

  lazy val routingController = {
    new RoutingController(envelopeCommandHandler)
  }

  lazy val healthRoutes = new HealthRoutes(httpErrorHandler, new uk.gov.hmrc.play.health.AdminController(configuration))

  lazy val appRoutes = new AppRoutes(httpErrorHandler, envelopeController, fileController, eventController,
    commandController, adminController)

  lazy val transferRoutes = new TransferRoutes(httpErrorHandler, transferController)

  lazy val routingRoutes = new RoutingRoutes(httpErrorHandler, routingController)

  lazy val metricsController = new MetricsController(metrics)
  lazy val adminRoutes = new AdminRoutes(httpErrorHandler, new Provider[MetricsController] {
    override def get(): MetricsController = metricsController
  })

  lazy val prodRoutes = new Routes(httpErrorHandler, appRoutes, transferRoutes, routingRoutes,
    healthRoutes, adminRoutes)

  lazy val testRoutes = new testOnlyDoNotUseInAppConf.Routes(httpErrorHandler, testOnlyController, prodRoutes)

  lazy val router: Router = if (configuration.getString("application.router").get == "testOnlyDoNotUseInAppConf.Routes") testRoutes else prodRoutes

  object ControllerConfiguration extends ControllerConfig {
    lazy val controllerConfigs = configuration.underlying.as[Config]("controllers")
  }

  object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
    lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
  }

  object MicroserviceAuditConnector extends AuditConnector with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"auditing")

    override def buildRequest(url: String)(implicit hc: HeaderCarrier): WSRequest = {
      wsApi.url(url).withHeaders(hc.headers: _*)
    }
  }

  object MicroserviceAuditFilter extends AuditFilter with AppName {
    override def mat = materializer

    override val auditConnector = MicroserviceAuditConnector

    override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
  }

  object MicroserviceLoggingFilter extends LoggingFilter {
    override def mat = materializer

    override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
  }


  lazy val loggingFilter: LoggingFilter = MicroserviceLoggingFilter

  lazy val microserviceAuditFilter: AuditFilter = MicroserviceAuditFilter

  lazy val metrics = new GraphiteMetricsImpl(applicationLifecycle, configuration)

  lazy val metricsFilter = new MetricsFilterImpl(metrics)

}
