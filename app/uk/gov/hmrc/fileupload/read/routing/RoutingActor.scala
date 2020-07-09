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
import cats.data.Xor
import play.api.{Configuration, Logger}
import play.api.libs.iteratee.Enumerator
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
class RoutingActor(
   config              :                                    RoutingConfig,
   subscribe           : (ActorRef, Class[_])            => Boolean,
   buildDownloadLink   : EnvelopeId                      => Future[String],
   lookupPublishUrl    : String                          => Option[String],
   findEnvelope        : EnvelopeId                      => Future[FindResult],
   getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Enumerator[Envelope],
   publishDownloadLink : (String, String)                => Future[RoutingRepository.PublishResult],
   handleCommand       : EnvelopeCommand                 => Future[Xor[CommandNotAccepted, CommandAccepted.type]]
 )(implicit executionContext: ExecutionContext
 ) extends Actor {

  import RoutingActor._

  val logger = Logger(getClass)

  implicit val actorMaterializer = akka.stream.ActorMaterializer()

  private val schedulers = List[Cancellable](
    // scheduler for checking scheduled pushes
    context.system.scheduler.schedule(
        initialDelay = config.initialDelay,
        interval     = config.interval,
        receiver     = self,
        message      = PushIfWaiting
    ))

  override def preStart =
    subscribe(self, classOf[Event])

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    super.preRestart(reason, message)
    logger.error(s"Unhandled exception for message: $message", reason)
  }

  override def postStop(): Unit =
    schedulers.foreach(_.cancel())

  // TODO prevent processing multiple at same time (block in the actor) - otherwise we may process Event and pick up same envelope with PushIfWaiting
  def receive = {
    case event: Event => event.eventData match {
      case e: EnvelopeRouteRequested =>
        findEnvelope(e.id).flatMap {
          case Xor.Right(envelope) =>
            routeEnvelope(envelope)
          case Xor.Left(e) =>
            Logger.warn(e.toString)
            Future.successful(())
        }

      case e: EventData =>
        logger.info(s"Not notifying for ${e.getClass.getName}")
    }

    case PushIfWaiting =>
      logger.info(s"Push any waiting messages")
      // TODO move conversion of play.enumerator to akka.source into getEnvelopesByStatus?
      import play.api.libs.streams.Streams
      import akka.stream.scaladsl.{Sink, Source}
      val s: Source[Envelope, akka.NotUsed] =
         Source.fromPublisher(Streams.enumeratorToPublisher(getEnvelopesByStatus(List(EnvelopeStatusRouteRequested), true)))
      s.mapAsync(parallelism = 1)(routeEnvelope).runWith(Sink.ignore)
  }

  def routeEnvelope(envelope: Envelope): Future[Unit] = {
    logger.info(s"Routing envelope [${envelope._id}] to: ${envelope.destination}")
    envelope.destination.flatMap(lookupPublishUrl).fold(Future.successful(true)){ publishUrl =>
      logger.info(s"envelope [${envelope._id}] to '${envelope.destination}' will be routed to '$publishUrl'")
      for {
        downloadLink <- buildDownloadLink(envelope._id)
        res          <- publishDownloadLink(downloadLink, publishUrl)
      } yield res match {
        case Xor.Right(())   => logger.info(s"Successfully published routing for envelope [${envelope._id}]")
                                true
        case Xor.Left(error) => logger.warn(s"Failed to publish routing for envelope [${envelope._id}]. Reason [${error.reason}]")
                                false
      }
    }.map { isRouted =>
      if (isRouted)
        // If this fails, consquence will be that it will be republished again... (once we set this up to run on a scheduler, picking up anything in RouteRequested state)
        // TODO to give up after x attempts, need to store attempt number somewhere...
        handleCommand(MarkEnvelopeAsRouted(envelope._id)).map {
          case Xor.Right(_) =>
          case Xor.Left(error) => logger.error(s"Could not mark envelope [${envelope._id}] as routed: $error")
        }.recover { case e => logger.error(s"Could not mark envelope [${envelope._id}] as routed: ${e.getMessage}", e) }
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
    getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Enumerator[Envelope],
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
