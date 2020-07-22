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

package uk.gov.hmrc.fileupload.read.routing

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.stream.scaladsl.{Sink, Source}
import cats.data.Xor
import org.joda.time.DateTime
import play.api.{Configuration, Logger}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, EnvelopeStatusRouteRequested, File}
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeRouteRequested, MarkEnvelopeAsRouted}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted, Event, EventData}
import uk.gov.hmrc.lock.LockRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** When the envelope is routed, if there is a registered endpoint, it will notify the recipient.
  * It checks periodically for files needing routing - this will retry any that previously failed to be delivered.
  */
class RoutingActor(
   config              :                                       RoutingConfig,
   buildNotification   : Envelope                           => Future[RoutingRepository.BuildNotificationResult],
   findEnvelope        : EnvelopeId                         => Future[FindResult],
   getEnvelopesByStatus: (List[EnvelopeStatus], Boolean)    => Source[Envelope, akka.NotUsed],
   pushNotification    : (FileTransferNotification, String) => Future[RoutingRepository.PushResult],
   handleCommand       : EnvelopeCommand                    => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
   lockRepository      :                                       LockRepository
 )(implicit executionContext: ExecutionContext
 ) extends Actor {

  import RoutingActor._

  val logger = Logger(getClass)

  implicit val actorMaterializer = akka.stream.ActorMaterializer()

  private var scheduler: Cancellable =
    context.system.scheduler.schedule(config.initialDelay, config.interval, self, PushIfWaiting)

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    super.preRestart(reason, message)
    logger.error(s"Unhandled exception for message: $message", reason)
  }

  override def postStop(): Unit =
    scheduler.cancel()

  def receive = {
    case PushIfWaiting =>
      logger.info(s"received PushIfWaiting")
      Lock.takeLock(lockRepository).flatMap {
        case None =>
          Future.successful(logger.info(s"no lock aquired"))
        case Some(lock) =>
          logger.info(s"aquired lock - pushing any waiting messages")
          getEnvelopesByStatus(List(EnvelopeStatusRouteRequested), true)
            .mapAsync(parallelism = 1)(routeEnvelope)
            .runWith(Sink.ignore)
            .andThen { case _ => lock.release() }
            .recover {
              case ex => logger.error(s"Failed to handle PushIfWaiting: ${ex.getMessage}", ex)
            }
      }
  }

  def routeEnvelope(envelope: Envelope): Future[Unit] = {
    // we may want to restrict pushing to a sender whitelist too
    logger.info(s"Routing envelope [${envelope._id}] from: ${envelope.sender} to: ${envelope.destination}")

    // we will push any envelope which has a pushUrl defined for the destination
    envelope.destination.flatMap(config.lookupPushUrl)
      .fold(Future.successful(MarkEnvelopeAsRouted(envelope._id, isPushed = false): EnvelopeCommand)){ pushUrl =>
        logger.info(s"envelope [${envelope._id}] to '${envelope.destination}' will be routed to '$pushUrl'")
        for {
          notificationRes <- buildNotification(envelope)
          notification    <- notificationRes match {
                               case Xor.Right(notification) => Future.successful(notification)
                               case Xor.Left(error)         => Future.failed(sys.error(s"Failed to build notification. Reason [${error.reason}]"))
                             }
          _               =  logger.info(s"will push $notification for envelope [${envelope._id}]")
          pushRes         <- pushNotification(notification, pushUrl)
          cmd             <- pushRes match {
                               case Xor.Right(())   => logger.info(s"Successfully pushed routing for envelope [${envelope._id}]")
                                                       Future.successful(MarkEnvelopeAsRouted(envelope._id, isPushed = true))
                               case Xor.Left(error) => Future.failed(sys.error(s"Failed to push routing for envelope [${envelope._id}] to ${envelope.destination}. Reason [${error.reason}]"))
                             }
        } yield cmd
    }.map(cmd =>
      handleCommand(cmd).map {
        case Xor.Right(_) =>
        case Xor.Left(error) => logger.error(s"Could not process $cmd for [${envelope._id}]: $error")
      }.recover { case e => logger.error(s"Could not process $cmd for [${envelope._id}]: ${e.getMessage}", e) }
    )
  }
}

object RoutingActor {
  case object PushIfWaiting

  def props(
    config              :                                       RoutingConfig,
    buildNotification   : Envelope                           => Future[RoutingRepository.BuildNotificationResult],
    findEnvelope        : EnvelopeId                         => Future[FindResult],
    getEnvelopesByStatus: (List[EnvelopeStatus], Boolean)    => Source[Envelope, akka.NotUsed],
    pushNotification    : (FileTransferNotification, String) => Future[RoutingRepository.PushResult],
    handleCommand       : EnvelopeCommand                    => Future[Xor[CommandNotAccepted, CommandAccepted.type]],
    lockRepository      :                                       LockRepository
  )(implicit executionContext: ExecutionContext
  ) =
    Props(new RoutingActor(
      config               = config,
      buildNotification    = buildNotification,
      findEnvelope         = findEnvelope,
      getEnvelopesByStatus = getEnvelopesByStatus,
      pushNotification     = pushNotification,
      handleCommand        = handleCommand,
      lockRepository       = lockRepository
    ))
}

case class Lock(release: () => Future[Unit])

object Lock {
  private val reqLockId = "RoutingActor"
  private val reqOwner = java.util.UUID.randomUUID().toString
  private val forceReleaseAfter = 1.hour

  def takeLock(lockRepository: LockRepository)(implicit ec: ExecutionContext): Future[Option[Lock]] = {
    lockRepository.lock(reqLockId, reqOwner, new org.joda.time.Duration(forceReleaseAfter.toMillis))
      .map { taken =>
        if (taken) Some(Lock(() => lockRepository.releaseLock(reqLockId, reqOwner)))
        else None
      }
  }
}
