/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.infrastructure

import akka.actor.{Actor, ActorLogging, Props}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.domain._
import uk.gov.hmrc.fileupload.read.envelope.Envelope
import uk.gov.hmrc.fileupload.write.envelope._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait ReportActor[S <: AnyRef] extends Actor with ActorLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  def id: EnvelopeId
  def get: (EnvelopeId) => Future[Option[S]]
  def save: (S) => Future[Boolean]
  def defaultState: (EnvelopeId) => S

  var currentState: S = defaultState(id)
  var eventVersion: Version = Version(0)
  var created: Created = Created(0)

  override def preStart = {
    currentState = Await.result(get(id).map(_.getOrElse(currentState)), 5.seconds)
  }

  def receive = {
    case Event(eventId, streamId, v: Version, created, eventType, eventData: EnvelopeEvent) =>
      eventVersion = v
      currentState = apply.applyOrElse((currentState, eventData), (input: (S, EventData)) => currentState)
      Await.result(save(currentState), 5 seconds)
    case _ => log.info("not implemented")
  }

  def apply: PartialFunction[(S, EventData), S]

//  def receive = {
//    case EnvelopeCreated =>
//      state = Envelope(_id = e.id, version = e.)
//
//    case e: FileQuarantined =>
//      val file = File(fileId = e.fileId, fileReferenceId = e.fileRefId, status = FileStatusQuarantined, name = Some(e.name), contentType = Some(e.contentType), metadata = Some(e.metadata))
//
//    case e: NoVirusDetected =>
//
//    case e: FileStored =>
//      log.info(s"$e not yet implemented")
//    case e: VirusDetected =>
//      log.info(s"$e not yet implemented")
//    case e: EnvelopeSealed =>
//      log.info(s"$e not yet implemented")
//    case a: AnyRef => {
//      println(a)
//    }
//  }

}
