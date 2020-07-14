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
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeRouteRequested, MarkEnvelopeAsRouted}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted, Event, EventData}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** When the envelope is routed, if there is a registered endpoint, it will notify the recipient.
  * It is triggered by the [[EnvelopeRouteRequested]] event, but also checks periodically for files needing routing, to retry.
  */
// TODO use mongolock to ensure only one instance is processing
class RoutingActor(
   config              :                                    RoutingConfig,
   buildDownloadLink   : EnvelopeId                      => Future[String],
   lookupPublishUrl    : String                          => Option[String],
   findEnvelope        : EnvelopeId                      => Future[FindResult],
   getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed],
   publishDownloadLink : (String, String)                => Future[RoutingRepository.PublishResult],
   handleCommand       : EnvelopeCommand                 => Future[Xor[CommandNotAccepted, CommandAccepted.type]]
 )(implicit executionContext: ExecutionContext
 ) extends Actor {

  import RoutingActor._

  val logger = Logger(getClass)

  implicit val actorMaterializer = akka.stream.ActorMaterializer()

  // rather than launching a scheduler to run at defined intervals, we'll set one to run once, each time we finish.
  // this ensures we don't end up with overlapping executions, and allows us to bring the next run forward, if we have
  // a relevant event.
  private var scheduler: Cancellable =
    context.system.scheduler.scheduleOnce(config.initialDelay, self, PushIfWaiting)

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    super.preRestart(reason, message)
    logger.error(s"Unhandled exception for message: $message", reason)
  }

  override def postStop(): Unit =
    scheduler.cancel()


  def receive = {
    case PushIfWaiting =>
      logger.info(s"Push any waiting messages")
      getEnvelopesByStatus(List(EnvelopeStatusRouteRequested), true)
        .mapAsync(parallelism = 1)(routeEnvelope)
        .runWith(Sink.ignore)
        .andThen {
          case _ => scheduler = context.system.scheduler.scheduleOnce(config.interval, self, PushIfWaiting)
        }
  }

  def routeEnvelope(envelope: Envelope): Future[Unit] = {
    // we may want to restrict pushing to a sender whitelist too
    val sender = envelope.metadata.flatMap(js => (js \ "sender" \ "service").asOpt[String])
    logger.info(s"Routing envelope [${envelope._id}] from: ${sender} to: ${envelope.destination}")

    // we will push any envelope which has a publishUrl defined for the destination
    envelope.destination.flatMap(lookupPublishUrl)
      .fold(Future.successful(Some(MarkEnvelopeAsRouted(envelope._id, isPushed = false)): Option[EnvelopeCommand])){ publishUrl =>
        logger.info(s"envelope [${envelope._id}] to '${envelope.destination}' will be routed to '$publishUrl'")
        for {
          downloadLink <- buildDownloadLink(envelope._id)
          res          <- publishDownloadLink(downloadLink, publishUrl)
        } yield res match {
          case Xor.Right(())   => logger.info(s"Successfully published routing for envelope [${envelope._id}]")
                                  Some(MarkEnvelopeAsRouted(envelope._id, isPushed = true))
          case Xor.Left(error) => logger.warn(s"Failed to publish routing for envelope [${envelope._id}] to ${envelope.destination}. Reason [${error.reason}]")
                                  None
        }
    }.map(_.map(cmd =>
      handleCommand(cmd).map {
        case Xor.Right(_) =>
        case Xor.Left(error) => logger.error(s"Could not process $cmd for [${envelope._id}]: $error")
      }.recover { case e => logger.error(s"Could not process $cmd for [${envelope._id}]: ${e.getMessage}", e) }
    ))
  }
}

object RoutingActor {
  case object PushIfWaiting

  def props(
    config              :                                    RoutingConfig,
    buildDownloadLink   : EnvelopeId                      => Future[String],
    lookupPublishUrl    : String                          => Option[String],
    findEnvelope        : EnvelopeId                      => Future[FindResult],
    getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed],
    publishDownloadLink : (String, String)                => Future[RoutingRepository.PublishResult],
    handleCommand       : EnvelopeCommand                 => Future[Xor[CommandNotAccepted, CommandAccepted.type]]
  )(implicit executionContext: ExecutionContext
  ) =
    Props(new RoutingActor(
      config               = config,
      buildDownloadLink    = buildDownloadLink,
      lookupPublishUrl     = lookupPublishUrl,
      findEnvelope         = findEnvelope,
      getEnvelopesByStatus = getEnvelopesByStatus,
      publishDownloadLink  = publishDownloadLink,
      handleCommand        = handleCommand
    ))
}
