/*
 * Copyright 2020 HM Revenue & Customs
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
import com.kenshoo.play.metrics.MetricsImpl
import javax.inject.{Inject, Singleton}
import play.api._
import play.api.libs.ws.ahc.AhcWSComponents
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteConcern
import uk.gov.hmrc.fileupload.controllers.RetrieveFile
import uk.gov.hmrc.fileupload.file.zip.Zippy
import uk.gov.hmrc.fileupload.infrastructure._
import uk.gov.hmrc.fileupload.read.envelope.{WithValidEnvelope, Service => EnvelopeService, _}
import uk.gov.hmrc.fileupload.read.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.read.routing.{RoutingActor, RoutingConfig, RoutingRepository}
import uk.gov.hmrc.fileupload.read.stats.{Stats, StatsActor, StatsLogWriter, StatsLogger, StatsLoggingConfiguration, StatsLoggingScheduler, Repository => StatsRepository}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.UnitOfWorkSerializer.{UnitOfWorkReader, UnitOfWorkWriter}
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, MongoEventStore, StreamId}
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class ApplicationModule @Inject()(
  servicesConfig: ServicesConfig,
  reactiveMongoComponent: ReactiveMongoComponent,
  auditConnector: AuditConnector,
  metrics: MetricsImpl,
  val applicationLifecycle: play.api.inject.ApplicationLifecycle,
  val configuration: play.api.Configuration,
  val environment: play.api.Environment,
  actorSystem: akka.actor.ActorSystem
)(implicit
  val executionContext: scala.concurrent.ExecutionContext,
  val materializer: akka.stream.Materializer
) extends AhcWSComponents {

  lazy val db = reactiveMongoComponent.mongoConnector.db

  implicit val reader = new UnitOfWorkReader(EventSerializer.toEventData)
  implicit val writer = new UnitOfWorkWriter(EventSerializer.fromEventData)

  val envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration =
    EnvelopeConstraintsConfiguration.getEnvelopeConstraintsConfiguration(configuration) match {
      case Right(envelopeConstraints) => envelopeConstraints
      case Left(failureReason) => throw new IllegalArgumentException(s"${failureReason.message}")
    }

  val envelopeHandler = new EnvelopeHandler(envelopeConstraintsConfigure)

  val subscribe: (ActorRef, Class[_]) => Boolean = actorSystem.eventStream.subscribe
  val publish: (AnyRef) => Unit = actorSystem.eventStream.publish
  val withBasicAuth: BasicAuth = BasicAuth(basicAuthConfiguration(configuration))

  val eventStore = if (environment.mode == Mode.Prod && configuration.getOptional[Boolean]("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    new MongoEventStore(db, metrics.defaultRegistry, writeConcern = WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true))
  } else {
    new MongoEventStore(db, metrics.defaultRegistry)
  }

  val updateEnvelope = if (environment.mode == Mode.Prod && configuration.getOptional[Boolean]("Prod.mongodb.replicaSetInUse").getOrElse(true)) {
    envelopeRepository.update(writeConcern = WriteConcern.ReplicaAcknowledged(n = 2, timeout = 5000, journaled = true)) _
  } else {
    envelopeRepository.update() _
  }

  def basicAuthConfiguration(config: Configuration): BasicAuthConfiguration = {
    def getUsers(config: Configuration): List[User] = {
      config.getOptional[String]("basicAuth.authorizedUsers").map { s =>
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

    config.getOptional[Boolean]("feature.basicAuthEnabled").getOrElse(false) match {
      case true => BasicAuthEnabled(getUsers(config))
      case false => BasicAuthDisabled
    }
  }

  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector,
    "file-upload", Some(t => Logger.warn(t.getMessage, t))) _

  // notifier
  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute, wsClient) _
  actorSystem.actorOf(NotifierActor.props(subscribe, findEnvelope, sendNotification), "notifierActor")
  actorSystem.actorOf(StatsActor.props(subscribe, findEnvelope, sendNotification, saveFileQuarantinedStat,
    deleteVirusDetectedStat, deleteFileStoredStat, deleteFiles), "statsActor")

  // initialize in-progress files logging actor
  StatsLoggingScheduler.initialize(actorSystem, statsLoggingConfiguration, new StatsLogger(statsRepository, new StatsLogWriter()))

  lazy val envelopeRepository = uk.gov.hmrc.fileupload.read.envelope.Repository.apply(db)

  lazy val getEnvelope = envelopeRepository.get _

  lazy val withValidEnvelope = new WithValidEnvelope(getEnvelope)

  lazy val findEnvelope = EnvelopeService.find(getEnvelope) _
  lazy val findMetadata = EnvelopeService.findMetadata(findEnvelope) _

  lazy val statsLoggingConfiguration = StatsLoggingConfiguration(configuration)
  lazy val statsRepository = StatsRepository.apply(db)
  lazy val saveFileQuarantinedStat = Stats.save(statsRepository.insert) _
  lazy val deleteFileStoredStat = Stats.deleteFileStored(statsRepository.delete) _
  lazy val deleteVirusDetectedStat = Stats.deleteVirusDetected(statsRepository.delete) _
  lazy val deleteFiles = Stats.deleteEnvelopeFiles(statsRepository.deleteAllInAnEnvelop) _
  lazy val allInProgressFile = Stats.all(statsRepository.all) _

  // envelope read model
  lazy val reportHandler = new EnvelopeReportHandler(
    toId = (streamId: StreamId) => EnvelopeId(streamId.value),
    updateEnvelope,
    envelopeRepository.delete,
    defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id))

  // command handler
  lazy val envelopeCommandHandler = {
    (command: EnvelopeCommand) =>
      new Aggregate[EnvelopeCommand, write.envelope.Envelope](
        handler          = envelopeHandler,
        defaultState     = () => write.envelope.Envelope(),
        publish          = publish,
        publishAllEvents = reportHandler.handle(replay = false)
      )(eventStore, executionContext).handleCommand(command)
  }

  lazy val getEnvelopesByStatus = envelopeRepository.getByStatus _

  lazy val deleteInProgressFile = statsRepository.deleteByFileRefId _

  lazy val nextId = () => EnvelopeId(UUID.randomUUID().toString)

  lazy val unitOfWorks = eventStore.unitsOfWorkForAggregate _
  lazy val publishAllEvents = reportHandler.handle(replay = true) _

  lazy val fileUploadFrontendBaseUrl = servicesConfig.baseUrl("file-upload-frontend")

  lazy val routingConfig = RoutingConfig(configuration)

  lazy val buildFileTransferNotification = RoutingRepository.buildFileTransferNotification(auditedHttpExecute, wsClient, routingConfig, fileUploadFrontendBaseUrl) _
  lazy val pushFileTransferNotification = RoutingRepository.pushFileTransferNotification(auditedHttpExecute, wsClient, routingConfig) _

  lazy val lockRepository = new LockRepository()(db)

  actorSystem.actorOf(
    RoutingActor.props(
      config = routingConfig,
      buildNotification = buildFileTransferNotification,
      findEnvelope,
      getEnvelopesByStatus,
      pushNotification = pushFileTransferNotification,
      handleCommand = envelopeCommandHandler,
      lockRepository = lockRepository
    ),
    "routingActor")

  lazy val getFileFromS3 = new RetrieveFile(wsClient, fileUploadFrontendBaseUrl).download _

  val getEnvelopesByDestination = envelopeRepository.getByDestination _
  val zipEnvelope = Zippy.zipEnvelope(findEnvelope, getFileFromS3) _

  val recreateCollections: List[() => Unit] = List(eventStore.recreate, envelopeRepository.recreate, statsRepository.recreate)

  val newId: () => String = () => UUID.randomUUID().toString
}
