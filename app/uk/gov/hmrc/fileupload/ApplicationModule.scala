/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import com.codahale.metrics.MetricRegistry
import org.apache.pekko.actor.ActorRef
import play.api._
import play.api.libs.ws.ahc.AhcWSComponents
import uk.gov.hmrc.fileupload.controllers.RetrieveFile
import uk.gov.hmrc.fileupload.file.zip.Zippy
import uk.gov.hmrc.fileupload.infrastructure._
import uk.gov.hmrc.fileupload.read.envelope.{WithValidEnvelope, Service => EnvelopeService, _}
import uk.gov.hmrc.fileupload.read.infrastructure.ReportHandler
import uk.gov.hmrc.fileupload.read.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.read.routing.{RoutingActor, RoutingConfig, RoutingRepository}
import uk.gov.hmrc.fileupload.read.stats.{Stats, StatsActor, StatsLogWriter, StatsLogger, StatsLoggingConfiguration, StatsLoggingScheduler, Repository => StatsRepository}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{Aggregate, Event, MongoEventStore, StreamId}
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}

import java.util.UUID
import javax.inject.{Inject, Singleton}

/**
  * This trait is added to control publishing the events in the tests
  */
@ImplementedBy(classOf[DefaultAllEventsPublisher])
trait AllEventsPublisher:
  def publish(reportHandler: ReportHandler[_, _], replay: Boolean): Seq[Event] => Unit

class DefaultAllEventsPublisher extends AllEventsPublisher:
  def publish(
    reportHandler: ReportHandler[_, _],
    replay       : Boolean
  ): Seq[Event] => Unit =
    reportHandler.handle(replay)

@Singleton
class ApplicationModule @Inject()(
  servicesConfig    : ServicesConfig,
  mongoComponent    : MongoComponent,
  allEventsPublisher: AllEventsPublisher,
  auditConnector    : AuditConnector,
  metricRegistry    : MetricRegistry,
  override val applicationLifecycle: play.api.inject.ApplicationLifecycle,
  override val configuration: play.api.Configuration,
  override val environment: play.api.Environment,
  actorSystem: org.apache.pekko.actor.ActorSystem
)(using
  override val executionContext: scala.concurrent.ExecutionContext,
  override val materializer    : org.apache.pekko.stream.Materializer
) extends AhcWSComponents:

  private val logger = Logger(getClass)

  val envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration =
    EnvelopeConstraintsConfiguration.getEnvelopeConstraintsConfiguration(configuration) match
      case Right(envelopeConstraints) => envelopeConstraints
      case Left(failureReason)        => throw IllegalArgumentException(s"${failureReason.message}")

  val envelopeHandler =
    EnvelopeHandler(envelopeConstraintsConfigure)

  val subscribe: (ActorRef, Class[_]) => Boolean =
    actorSystem.eventStream.subscribe

  val publish: (AnyRef) => Unit =
    actorSystem.eventStream.publish

  val eventStore =
    MongoEventStore(mongoComponent, metricRegistry)

  val updateEnvelope =
    envelopeRepository.update() _

  lazy val auditedHttpExecute =
    PlayHttp.execute(
      auditConnector,
      "file-upload",
      Some(t => logger.warn(t.getMessage, t))
    ) _

  // notifier
  lazy val sendNotification =
    NotifierRepository.notify(auditedHttpExecute, wsClient) _

  actorSystem.actorOf(
    NotifierActor.props(
      subscribe,
      findEnvelope,
      sendNotification
    ),
    "notifierActor"
  )

  actorSystem.actorOf(
    StatsActor.props(
      subscribe,
      findEnvelope,
      saveFileQuarantinedStat,
      deleteVirusDetectedStat,
      deleteFileStoredStat,
      deleteFiles
    ),
    "statsActor"
  )

  // initialize in-progress files logging actor
  StatsLoggingScheduler.initialize(actorSystem, statsLoggingConfiguration, StatsLogger(statsRepository, StatsLogWriter()))

  lazy val envelopeRepository =
    uk.gov.hmrc.fileupload.read.envelope.Repository.apply(mongoComponent)

  lazy val getEnvelope =
    envelopeRepository.get _

  lazy val withValidEnvelope =
    WithValidEnvelope(getEnvelope)

  lazy val findEnvelope =
    EnvelopeService.find(getEnvelope) _

  lazy val findMetadata =
    EnvelopeService.findMetadata(findEnvelope) _

  lazy val statsLoggingConfiguration = StatsLoggingConfiguration(configuration)
  lazy val statsRepository           = StatsRepository.apply(mongoComponent)
  lazy val saveFileQuarantinedStat   = Stats.save(statsRepository.insert) _
  lazy val deleteFileStoredStat      = Stats.deleteFileStored(statsRepository.delete) _
  lazy val deleteVirusDetectedStat   = Stats.deleteVirusDetected(statsRepository.delete) _
  lazy val deleteFiles               = Stats.deleteEnvelopeFiles(statsRepository.deleteAllInAnEnvelop) _
  lazy val allInProgressFile         = Stats.all(statsRepository.all _) _

  // envelope read model
  lazy val reportHandler =
    EnvelopeReportHandler(
      toId = (streamId: StreamId) => EnvelopeId(streamId.value),
      updateEnvelope,
      envelopeRepository.delete,
      defaultState = (id: EnvelopeId) => uk.gov.hmrc.fileupload.read.envelope.Envelope(id)
    )

  // command handler
  lazy val envelopeCommandHandler =
    (command: EnvelopeCommand) =>
      Aggregate[EnvelopeCommand, write.envelope.Envelope](
        handler          = envelopeHandler,
        defaultState     = () => write.envelope.Envelope(),
        publish          = publish,
        publishAllEvents = allEventsPublisher.publish(reportHandler, replay = false)
      )(using eventStore, executionContext).handleCommand(command)

  lazy val getEnvelopesByStatus =
    envelopeRepository.getByStatus _

  lazy val deleteInProgressFile =
    statsRepository.deleteByFileRefId _

  lazy val nextId =
    () => EnvelopeId(UUID.randomUUID().toString)

  lazy val unitOfWorks =
    eventStore.unitsOfWorkForAggregate _

  lazy val publishAllEventsWithReplay =
    allEventsPublisher.publish(reportHandler, replay = true)

  lazy val fileUploadFrontendBaseUrl =
    servicesConfig.baseUrl("file-upload-frontend")

  lazy val getFileFromS3 = RetrieveFile(wsClient, fileUploadFrontendBaseUrl).download _
  lazy val getZipData    = RetrieveFile(wsClient, fileUploadFrontendBaseUrl).getZipData _
  lazy val downloadZip   = RetrieveFile(wsClient, fileUploadFrontendBaseUrl).downloadZip _

  lazy val routingConfig =
    RoutingConfig(configuration)

  lazy val buildFileTransferNotification =
    RoutingRepository.buildFileTransferNotification(getZipData, routingConfig) _

  lazy val pushFileTransferNotification =
    RoutingRepository.pushFileTransferNotification(auditedHttpExecute, wsClient, routingConfig) _

  lazy val lockRepository =
    MongoLockRepository(mongoComponent, CurrentTimestampSupport())

  actorSystem.actorOf(
    RoutingActor.props(
      config                  = routingConfig,
      buildNotification       = buildFileTransferNotification,
      getEnvelopesByStatusDMS = envelopeRepository.getByStatusDMS _,
      pushNotification        = pushFileTransferNotification,
      handleCommand           = envelopeCommandHandler,
      lockRepository          = lockRepository,
      applicationLifecycle    = applicationLifecycle,
      markAsSeen              = envelopeRepository.markAsSeen
    ),
    "routingActor"
  )

  val getEnvelopesByDestination =
    envelopeRepository.getByDestination _

  val zipEnvelope =
    Zippy.zipEnvelope(findEnvelope, downloadZip) _

  val recreateCollections: List[() => Unit] =
    List(eventStore.recreate _, envelopeRepository.recreate _, statsRepository.recreate _)

  val newId: () => String =
    () => UUID.randomUUID().toString

  OldDataPurger(
    configuration,
    eventStore,
    envelopeRepository,
    lockRepository,
    java.time.Instant.now _
  )(using
    executionContext,
    actorSystem
  ).purge()

end ApplicationModule
