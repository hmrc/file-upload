/*
 * Copyright 2018 HM Revenue & Customs
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

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.ActorRef
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import com.kenshoo.play.metrics.{MetricsController, MetricsFilterImpl, MetricsImpl}
import com.typesafe.config.Config
import javax.inject.Provider
import net.ceedubs.ficus.Ficus._
import play.api.ApplicationLoader.Context
import play.api.Mode.Mode
import play.api._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import reactivemongo.api.commands
import uk.gov.hmrc.fileupload.admin.{Routes => AdminRoutes}
import uk.gov.hmrc.fileupload.app.{Routes => AppRoutes}
import uk.gov.hmrc.fileupload.controllers._
import uk.gov.hmrc.fileupload.controllers.routing.RoutingController
import uk.gov.hmrc.fileupload.controllers.transfer.TransferController
import uk.gov.hmrc.fileupload.file.zip.Zippy
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}
import uk.gov.hmrc.fileupload.infrastructure._
import uk.gov.hmrc.fileupload.manualdihealth.{Routes => HealthRoutes}
import uk.gov.hmrc.fileupload.prod.Routes
import uk.gov.hmrc.fileupload.read.envelope.{WithValidEnvelope, Service => EnvelopeService, _}
import uk.gov.hmrc.fileupload.read.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.read.stats.{Stats, StatsActor}
import uk.gov.hmrc.fileupload.routing.{Routes => RoutingRoutes}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.{Routes => TransferRoutes}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, MongoEventStore, StreamId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, _}


class ApplicationLoader extends play.api.ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    val app = new ApplicationModule(context)
    app.graphiteStart()
    app.application
  }
}

class ApplicationModule(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents with AppName with ServicesConfig {

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  override lazy val mode: Mode = context.environment.mode
  override lazy val runModeConfiguration: Configuration = configuration

  val envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration = {
    EnvelopeConstraintsConfiguration.getEnvelopeConstraintsConfiguration(runModeConfiguration) match {
      case Right(envelopeConstraints) ⇒ envelopeConstraints
      case Left(failureReason) ⇒ throw new IllegalArgumentException(s"${failureReason}")
    }
  }

  val envelopeHandler = new EnvelopeHandler(envelopeConstraintsConfigure)

  val subscribe: (ActorRef, Class[_]) => Boolean = actorSystem.eventStream.subscribe
  val publish: (AnyRef) => Unit = actorSystem.eventStream.publish
  val withBasicAuth: BasicAuth = BasicAuth(basicAuthConfiguration(configuration))

  val eventStore = if (environment.mode == Mode.Prod && configuration.getBoolean("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    new MongoEventStore(db, metrics.defaultRegistry, writeConcern = commands.WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true))
  } else {
    new MongoEventStore(db, metrics.defaultRegistry)
  }

  val updateEnvelope = if (environment.mode == Mode.Prod && configuration.getBoolean("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    envelopeRepository.update(writeConcern = commands.WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true)) _
  } else {
    envelopeRepository.update() _
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

  lazy val db = new ReactiveMongoComponentImpl(configuration, environment, applicationLifecycle).mongoConnector.db

  // notifier
  actorSystem.actorOf(NotifierActor.props(subscribe, findEnvelope, sendNotification), "notifierActor")
  actorSystem.actorOf(StatsActor.props(subscribe, findEnvelope, sendNotification, saveFileQuarantinedStat,
    deleteVirusDetectedStat, deleteFileStoredStat, deleteFiles), "statsActor")


  override lazy val httpFilters: Seq[EssentialFilter] = Seq(
    new UserAgentRequestFilter(metrics.defaultRegistry, UserAgent.allKnown, UserAgent.defaultIgnoreList),
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

  lazy val findEnvelope = EnvelopeService.find(getEnvelope) _
  lazy val findMetadata = EnvelopeService.findMetadata(findEnvelope) _

  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute, wsClient) _


  lazy val statsRepository = uk.gov.hmrc.fileupload.read.stats.Repository.apply(db)
  lazy val saveFileQuarantinedStat = Stats.save(statsRepository.insert) _
  lazy val deleteFileStoredStat = Stats.deleteFileStored(statsRepository.delete) _
  lazy val deleteVirusDetectedStat = Stats.deleteVirusDetected(statsRepository.delete) _
  lazy val deleteFiles = Stats.deleteEnvelopeFiles(statsRepository.deleteAllInAnEnvelop) _
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
      nextId = nextId,
      handleCommand = envelopeCommandHandler,
      findEnvelope = findEnvelope,
      findMetadata = findMetadata,
      findAllInProgressFile = allInProgressFile,
      deleteInProgressFile = statsRepository.deleteByFileRefId,
      getEnvelopesByStatus = getEnvelopesByStatus,
      envelopeConstraintsConfigure = envelopeConstraintsConfigure)
  }

  lazy val eventController = {
    new EventController(eventStore.unitsOfWorkForAggregate, createReportHandler.handle(replay = true))
  }

  lazy val commandController = {
    new CommandController(envelopeCommandHandler)
  }

  lazy val fileUploadFrontendBaseUrl = baseUrl("file-upload-frontend")

  lazy val getFileFromS3 = new RetrieveFile(wsClient, fileUploadFrontendBaseUrl).download _

  lazy val fileController = {
    new FileController(
      withBasicAuth = withBasicAuth,
      retrieveFileS3 = getFileFromS3,
      withValidEnvelope = withValidEnvelope,
      handleCommand = envelopeCommandHandler)
  }

  lazy val transferController = {
    val getEnvelopesByDestination = envelopeRepository.getByDestination _
    //Todo: remove getFileFromMongoDB when mongoDB is not in use at all.
    val zipEnvelope = Zippy.zipEnvelope(findEnvelope, getFileFromS3) _
    new TransferController(withBasicAuth, getEnvelopesByDestination, envelopeCommandHandler, zipEnvelope)
  }

  lazy val testOnlyController = {
    new TestOnlyController(recreateCollections = List(eventStore.recreate, envelopeRepository.recreate, statsRepository.recreate))
  }

  lazy val routingController = new RoutingController(envelopeCommandHandler)

  lazy val healthRoutes = new HealthRoutes(httpErrorHandler, new uk.gov.hmrc.play.health.AdminController(configuration))

  lazy val appRoutes = new AppRoutes(httpErrorHandler, envelopeController, fileController, eventController,
    commandController)

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

  object AuthParamsControllerConfiguration {
    lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
  }

  object MicroserviceAuditConnector extends AuditConnector with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
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

  // Don't use uk.gov.hmrc.play.graphite.GraphiteMetricsImpl as it won't allow hot reload due to overridden onStop() method
  lazy val metrics = new MetricsImpl(applicationLifecycle, configuration)

  lazy val metricsFilter = new MetricsFilterImpl(metrics)

  def graphiteStart(): Unit = {

    val graphiteConfig = configuration.getConfig(s"$env.microservice.metrics")

    def enabled: Boolean = {
      val status = metricsPluginEnabled && graphitePublisherEnabled
      Logger.info(s"graphitePublisherEnabled: $env=$status")
      status
    }

    def metricsPluginEnabled: Boolean = configuration.getBoolean("metrics.enabled").getOrElse(false)

    def graphitePublisherEnabled: Boolean = graphiteConfig.flatMap(
      _.getBoolean("graphite.enabled")).getOrElse(false)

    if (enabled) {
      val metricsConfig = graphiteConfig.getOrElse(throw new Exception("The application does not contain required metrics configuration"))

      val graphite = new Graphite(new InetSocketAddress(
        metricsConfig.getString("graphite.host").getOrElse("graphite"),
        metricsConfig.getInt("graphite.port").getOrElse(2003)))

      val prefix = metricsConfig.getString("graphite.prefix").getOrElse(s"tax.${configuration.getString("appName")}")

      import java.util.concurrent.TimeUnit._

      val reporter = GraphiteReporter.forRegistry(
        SharedMetricRegistries.getOrCreate(configuration.getString("metrics.name").getOrElse("default")))
        .prefixedWith(s"$prefix.${java.net.InetAddress.getLocalHost.getHostName}")
        .convertRatesTo(SECONDS)
        .convertDurationsTo(MILLISECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite)

      reporter.start(metricsConfig.getLong("graphite.interval").getOrElse(10L), SECONDS)
    }
  }

}
