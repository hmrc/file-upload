/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.{Actor, Cancellable, Props}
import akka.stream.scaladsl.{Sink, Source}
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, MarkEnvelopeAsRouted}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted}
import uk.gov.hmrc.mongo.lock.LockRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong

/** When the envelope is routed, if there is a registered endpoint, it will notify the recipient.
  * It checks periodically for files needing routing - this will retry any that previously failed to be delivered.
  */
class RoutingActor(
   config              :                                    RoutingConfig,
   buildNotification   : Envelope                        => Future[RoutingRepository.BuildNotificationResult],
   findEnvelope        : EnvelopeId                      => Future[FindResult],
   getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed],
   pushNotification    : FileTransferNotification        => Future[RoutingRepository.PushResult],
   handleCommand       : EnvelopeCommand                 => Future[Either[CommandNotAccepted, CommandAccepted.type]],
   lockRepository      :                                    LockRepository
 )(implicit executionContext: ExecutionContext
 ) extends Actor {

  import RoutingActor._

  val logger = Logger(getClass)

  implicit val actorMaterializer = akka.stream.ActorMaterializer()

  private val scheduler: Cancellable =
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
            .mapAsync(parallelism = 1)(envelope =>
              routeEnvelope(envelope)
              .recover {
                case ex => // alerting is configured to trigger off this message
                           logger.error(s"Failed to route envelope [${envelope._id}]: ${ex.getMessage}", ex)
              }
            )
            .runWith(Sink.ignore)
            .andThen { case _ => lock.release() }
            .recoverWith {
              case ex => logger.error(s"Failed to handle PushIfWaiting: ${ex.getMessage}", ex)
                         Future.failed(ex)
            }
      }
  }

  def routeEnvelope(envelope: Envelope): Future[Unit] = {
    logger.info(s"Routing envelope [${envelope._id}] to: ${envelope.destination}")

    // we will push any envelope which has a destination in configuration list
    envelope.destination.filter(config.destinations.contains)
      .fold(Future.successful(MarkEnvelopeAsRouted(envelope._id, isPushed = false): EnvelopeCommand)){ destination =>
        logger.info(s"envelope [${envelope._id}] to '$destination' will be routed to '${config.pushUrl}'")
        for {
          notificationRes <- buildNotification(envelope)
          notification    <- notificationRes match {
                               case Right(notification) => Future.successful(notification)
                               case Left(error)         => fail(s"Failed to build notification. Reason [${error.reason}]")
                             }
          _               =  logger.info(s"will push $notification for envelope [${envelope._id}]")
          pushRes         <- pushNotification(notification)
          cmd             <- pushRes match {
                               case Right(())   => logger.info(s"Successfully pushed routing for envelope [${envelope._id}]")
                                                   Future.successful(MarkEnvelopeAsRouted(envelope._id, isPushed = true))
                               case Left(error) => fail(s"Failed to push routing for envelope [${envelope._id}]. Reason [${error.reason}]")
                             }
        } yield cmd
    }.map(cmd =>
      handleCommand(cmd).map {
        case Right(_) =>
        case Left(error) => fail(s"Could not process $cmd for [${envelope._id}]: $error")
      }.recover { case e => fail(s"Could not process $cmd for [${envelope._id}]: ${e.getMessage}", e) }
    )
  }

  def fail[T](msg: String): Future[T] =
    Future.failed(new RuntimeException(msg))

  def fail[T](msg: String, t: Throwable): Future[T] =
    Future.failed(new RuntimeException(msg, t))
}

object RoutingActor {
  case object PushIfWaiting

  def props(
    config              :                                    RoutingConfig,
    buildNotification   : Envelope                        => Future[RoutingRepository.BuildNotificationResult],
    findEnvelope        : EnvelopeId                      => Future[FindResult],
    getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed],
    pushNotification    : FileTransferNotification        => Future[RoutingRepository.PushResult],
    handleCommand       : EnvelopeCommand                 => Future[Either[CommandNotAccepted, CommandAccepted.type]],
    lockRepository      :                                    LockRepository
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
    lockRepository.takeLock(reqLockId, reqOwner, ttl = forceReleaseAfter)
      .map { taken =>
        if (taken) Some(Lock(() => lockRepository.releaseLock(reqLockId, reqOwner)))
        else None
      }
  }
}
