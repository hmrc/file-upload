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

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object EnvelopeStorage{
  case class FindById(id: BSONObjectID)

  def props(envelopeRepository: EnvelopeRepository): Props = Props(classOf[EnvelopeStorage], envelopeRepository)

}

class EnvelopeStorage(val envelopeRepository: EnvelopeRepository) extends Actor with ActorLogging{
  import EnvelopeStorage._

  implicit val ex: ExecutionContext = context.dispatcher

  override def preStart(): Unit = {
    log.info("Envelope storage online")
  }

  def receive = {
    case FindById(id) =>
      log.info(s"processing find request for id $id")
      findEnvelopeById(id, sender())
  }

  def findEnvelopeById(byId: BSONObjectID, recipient: ActorRef): Unit = {
    envelopeRepository.get(byId).onComplete{
      case Success(result) => recipient ! result
      case Failure(t) => throw t
    }
  }

  override def postStop(): Unit = log.info("Envelope storage is going offline")
}
