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

package uk.gov.hmrc.fileupload.read.notifier

import akka.actor.{Actor, ActorRef, Props}
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.read.envelope.Service.FindResult
import uk.gov.hmrc.fileupload.read.notifier.NotifierRepository.PublishResult
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeRouted
import uk.gov.hmrc.fileupload.write.infrastructure.{Event, EventData}

import scala.concurrent.{ExecutionContext, Future}

// Notifies the backend when the file is routed
class RoutingNotifierActor(
   subscribe          : (ActorRef, Class[_]) => Boolean,
   buildDownloadLink  : EnvelopeId           => Future[String],
   lookupPublishUrl   : String               => Option[String],
   findEnvelope       : EnvelopeId           => Future[FindResult],
   publishDownloadLink: (String, String)     => Future[PublishResult])
 (implicit executionContext: ExecutionContext
 ) extends Actor {

  val logger = Logger(getClass)

  override def preStart =
    subscribe(self, classOf[Event])

  def receive = {
    case event: Event => event.eventData match {
      case e: EnvelopeRouted =>
        findEnvelope(e.id).flatMap {
          case Xor.Right(envelope) =>
            logger.info(s"Routing envelope [${e.id}] to: ${envelope.destination}")
            envelope.destination.flatMap(lookupPublishUrl).map { publishUrl =>
              logger.info(s"envelope [${e.id}] to '${envelope.destination}' will be routed to '$publishUrl'")
              for {
                downloadLink <- buildDownloadLink(envelope._id)
                res          <- publishDownloadLink(downloadLink, publishUrl)
              } yield res match {
                case Xor.Right(())   => logger.info(s"Successfully published routing for envelope [${e.id}]")
                case Xor.Left(error) => logger.warn(s"Failed to publish routing for envelope [${e.id}]. Reason [${error.reason}]")
              }
            }.getOrElse(Future.successful(()))
          case Xor.Left(e) =>
            Logger.warn(e.toString)
            Future.successful(())
        }

      case e: EventData =>
        logger.info(s"Not notifying for ${e.getClass.getName}")
    }
  }
}

object RoutingNotifierActor {
  def props(
    subscribe          : (ActorRef, Class[_]) => Boolean,
    buildDownloadLink  : EnvelopeId           => Future[String],
    lookupPublishUrl   : String               => Option[String],
    findEnvelope       : EnvelopeId           => Future[FindResult],
    publishDownloadLink: (String, String)     => Future[PublishResult])
  (implicit executionContext: ExecutionContext
  ) =
    Props(new RoutingNotifierActor(
      subscribe           = subscribe,
      buildDownloadLink   = buildDownloadLink,
      lookupPublishUrl    = lookupPublishUrl,
      findEnvelope        = findEnvelope,
      publishDownloadLink = publishDownloadLink
    ))
}
