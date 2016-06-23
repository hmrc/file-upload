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


import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.iteratee.Input

import scala.concurrent.Future
import play.api.mvc.BodyParser
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository
import uk.gov.hmrc.fileupload.ByteStream
import scala.language.postfixOps
import scala.util.{Success, Failure}
import scala.util.control.NonFatal


object FileUploader{
  trait Status
  case object EOF
  case object Uploading extends Status
  case object Completed extends Status
  case class Failed(reason: String) extends Status
  case class Chunk(envelopeId: String, fileId: String, bytes: ByteStream)

  def props(envelopeId: String, fileId: String, envelopeRepository: EnvelopeRepository): Props = Props(classOf[FileUploader], envelopeId, fileId, envelopeRepository)

	def parseBody(envelopeId: String, fileId: String) : BodyParser[String] = BodyParser{ _ =>

		implicit val ec = Actors.actorSystem.dispatcher
		val fileUploader = Actors.fileUploader(envelopeId, fileId)

		IterateeAdapter(fileUploader)
	}
}

class FileUploader(envelopeId: String, fileId: String, envelopeRepository: EnvelopeRepository) extends Actor with ActorLogging{
	import FileUploader._

  implicit val ec = context.dispatcher
  var status: Status = Uploading
  var sink = Future.successful(envelopeRepository.iterateeForUpload(fileId))


  override def preStart = log.info(s"processing upload request for $envelopeId")


  def receive = {
    case stream : ByteStream   =>
      sink = sink.flatMap(itr => itr.feed(Input.El(stream)))
    case EOF =>
      sink = sink.flatMap(itr => itr.feed(Input.EOF))
      status = Completed
      val replyTo = sender
      sink.onComplete{
        case Failure(NonFatal(e)) => replyTo ! Failed(e.getMessage)
        case Success(_) => replyTo ! status
      }

  }

  override def postStop = log.info(s"file upload request for $envelopeId complete")

}
