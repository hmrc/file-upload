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

package uk.gov.hmrc.fileupload.read.routing

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.testkit.TestKit
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doNothing, when}
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus, EnvelopeStatusRouteRequested}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure.{CommandAccepted, CommandError, CommandNotAccepted}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.lock.LockRepository

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationLong
import ExecutionContext.Implicits.global
import java.util.concurrent.atomic.AtomicBoolean
import org.joda.time.DateTime

class RoutingActorSpec
  extends TestKit(ActorSystem("RoutingActorSpec"))
     with AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with ScalaFutures
     with Eventually
     with IntegrationPatience
     with OptionValues
     with BeforeAndAfterAll {
  import RoutingRepository._

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  "RoutingActor" should {
    "buildNotification for envelopes for push" in {
      val buildNotificationCalled = new AtomicBoolean(false)

      val boot = Boot(
        buildNotification       = envelope => {
                                    buildNotificationCalled.set(true)
                                    Future.successful(Left(BuildNotificationError(envelopeId = envelope._id, reason = "failed", isTransient = false)))
                                  },
        getEnvelopesByStatusDMS = (statuses, isDMS, onlyUnseen) => if (isDMS && statuses.contains(EnvelopeStatusRouteRequested))
                                                                     Source.single(Envelope(destination = Some("dms")))
                                                                   else Source.empty
      )
      boot.routingActor ! RoutingActor.PushIfWaiting
      eventually {
        buildNotificationCalled.get shouldBe true
      }
    }

    "buildNotification for pushed envelopes if haven't been pushed for a while" in {
      val buildNotificationCalled = new AtomicBoolean(false)

      val boot = Boot(
        buildNotification       = envelope => {
                                    buildNotificationCalled.set(true)
                                    Future.successful(Left(BuildNotificationError(envelopeId = envelope._id, reason = "failed", isTransient = false)))
                                  },
        getEnvelopesByStatusDMS = (statuses, isDMS, onlyUnseen) => if (isDMS && statuses.contains(EnvelopeStatusRouteRequested))
                                                                     Source.single(Envelope(
                                                                       destination = Some("dms"),
                                                                       lastPushed  = Some(DateTime.now().minusMinutes(11)) // we've configured pushRetryBackoff to 10 mins
                                                                     ))
                                                                   else Source.empty
      )
      boot.routingActor ! RoutingActor.PushIfWaiting
      eventually {
        buildNotificationCalled.get shouldBe true
      }
    }
  }

  case class Boot(
    buildNotification       : Envelope                        => Future[BuildNotificationResult] =
      envelope => Future.successful(Left(BuildNotificationError(envelopeId = envelope._id, reason = "failed", isTransient = false))),

    getEnvelopesByStatusDMS : (List[EnvelopeStatus],
                                Boolean,
                                Boolean)                       => Source[Envelope, org.apache.pekko.NotUsed] =
      (evelopeStatuses, isDMS, onlyUnseen) => Source.single(Envelope()),

    pushNotification        : FileTransferNotification        => Future[PushResult] =
      (notification) => Future.successful(Left(PushError(correlationId = notification.audit.correlationId, reason = "failed"))),

    handleCommand           : EnvelopeCommand                 => Future[Either[CommandNotAccepted, CommandAccepted.type]] =
      command => Future.successful(Left(CommandError("failed"))),

     markAsSeen              : EnvelopeId                      => Future[Unit] =
      envId => Future.unit
  ) {

    val routingConfig = RoutingConfig(
      initialDelay      = 10.minutes, // we'll request it
      interval          = 10.minutes,
      clientId          = "clientId",
      recipientOrSender = "recipientOrSender",
      pushUrl           = "pushUrl",
      destinations      = List("dms"),
      informationType   = "informationType",
      throttleElements  = 10,
      throttlePer       = 1.minute,
      pushRetryBackoff  = 10.minutes
    )

    val lockRepository = mock[LockRepository]
    when(lockRepository.takeLock(any, any, any))
      .thenReturn(Future.successful(Some(mock[uk.gov.hmrc.mongo.lock.Lock])))
    when(lockRepository.releaseLock(any, any))
      .thenReturn(Future.unit)

    val applicationLifecycle = mock[ApplicationLifecycle]
    doNothing.when(applicationLifecycle).addStopHook(any[() => Future[Unit]])

    val routingActor = system.actorOf(
      RoutingActor.props(
        config                  = routingConfig,
        buildNotification       = buildNotification,
        getEnvelopesByStatusDMS = getEnvelopesByStatusDMS,
        pushNotification        = pushNotification,
        handleCommand           = handleCommand,
        lockRepository          = lockRepository,
        applicationLifecycle    = applicationLifecycle,
        markAsSeen              = markAsSeen
      ),
      s"routingTestActor${scala.util.Random.nextInt()}"
    )
  }
}
