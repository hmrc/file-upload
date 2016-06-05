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

package uk.gov.hmrc.fileupload.actors

import akka.actor.{ActorLogging, Props, ActorRef, Actor}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.actors.EnvelopeManager.GetEnvelope
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


object EnvelopeManager{
  case class GetEnvelope(id: String)

  def props(storage: ActorRef): Props = Props(classOf[EnvelopeManager], storage)
}

class EnvelopeManager(storage: ActorRef) extends Actor with ActorLogging{

  implicit val ex: ExecutionContext = context.dispatcher

  override def preStart(): Unit = {
    log.info("Envelope manager online")
  }

  def receive = {
    case GetEnvelope(id) =>
      log.info(s"processing GetEnvelope($id)")
      storage forward (EnvelopeStorage FindById BSONObjectID(id))
  }

  override def postStop(): Unit = log.info("Envelope storage is going offline")
}
