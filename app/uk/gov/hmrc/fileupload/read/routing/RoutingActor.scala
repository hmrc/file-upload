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
import play.api.{Configuration, Logger}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.write.envelope.{EnvelopeCommand, EnvelopeRouteRequested, MarkEnvelopeAsRouted, MarkEnvelopeAsPushAttempted}
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandNotAccepted, Event, EventData}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationLong, FiniteDuration}

/** When the envelope is routed, if there is a registered endpoint, it will notify the recipient.
  * It is triggered by the [[EnvelopeRouteRequested]] event, but also checks periodically for files needing routing, to retry.
  */
class RoutingActor(
   config              :                                    RoutingConfig,
   subscribe           : (ActorRef, Class[_])            => Boolean,
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

  override def preStart =
    subscribe(self, classOf[Event])

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    super.preRestart(reason, message)
    logger.error(s"Unhandled exception for message: $message", reason)
  }

  override def postStop(): Unit =
    scheduler.cancel()


  def receive = {
    case event: Event => event.eventData match {
      case e: EnvelopeRouteRequested =>
        // let's bring the next poll forward since we know we have a new event
        // but wait a little, to ensure the new event has been serialised by another event subscriber (or we won't pick up destination)
        scheduler.cancel()
        scheduler = context.system.scheduler.scheduleOnce(500.millis, self, PushIfWaiting)

      case e: EventData =>
        logger.debug(s"Not notifying for ${e.getClass.getName}")
    }

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
    logger.info(s"Routing envelope [${envelope._id}] from: ${sender} to: ${envelope.destination} (numPushAttempts= ${envelope.numPushAttempts})")

    // we will push any envelope which has a publishUrl defined for the destination
    envelope.destination.flatMap(lookupPublishUrl)
      .filter(_ => envelope.numPushAttempts.getOrElse(0) < config.maxNumPushAttempts) // this will mark the file as routed when we reach the maximum - TODO is it better to move to another state, e.g. MarkAsUndelivered?
      .fold(Future.successful(MarkEnvelopeAsRouted(envelope._id, isPushed = false): EnvelopeCommand)){ publishUrl =>
        logger.info(s"envelope [${envelope._id}] to '${envelope.destination}' will be routed to '$publishUrl'")
        for {
          downloadLink <- buildDownloadLink(envelope._id)
          res          <- publishDownloadLink(downloadLink, publishUrl)
        } yield res match {
          case Xor.Right(())   => logger.info(s"Successfully published routing for envelope [${envelope._id}]")
                                  MarkEnvelopeAsRouted(envelope._id, isPushed = true)
          case Xor.Left(error) => logger.warn(s"Failed to publish routing for envelope [${envelope._id}]. Reason [${error.reason}]")
                                  MarkEnvelopeAsPushAttempted(envelope._id)
        }
    }.map { cmd =>
      handleCommand(cmd).map {
        case Xor.Right(_) =>
        case Xor.Left(error) => logger.error(s"Could not process $cmd for [${envelope._id}]: $error")
      }.recover { case e => logger.error(s"Could not process $cmd for [${envelope._id}]: ${e.getMessage}", e) }
    }
  }
}

object RoutingActor {
  case object PushIfWaiting

  def props(
    config              :                                    RoutingConfig,
    subscribe           : (ActorRef, Class[_])            => Boolean,
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
      subscribe            = subscribe,
      buildDownloadLink    = buildDownloadLink,
      lookupPublishUrl     = lookupPublishUrl,
      findEnvelope         = findEnvelope,
      getEnvelopesByStatus = getEnvelopesByStatus,
      publishDownloadLink  = publishDownloadLink,
      handleCommand        = handleCommand
    ))
}
