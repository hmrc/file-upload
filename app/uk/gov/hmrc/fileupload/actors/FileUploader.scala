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


import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.pattern._
import akka.util.Timeout
import play.api.libs.iteratee.Input.El
import play.api.libs.iteratee.{Input, Iteratee}
import play.api.mvc.{AnyContentAsText, Results, BodyParser}
import uk.gov.hmrc.fileupload.repositories.EnvelopeRepository
import uk.gov.hmrc.fileupload.ByteStream
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}



object FileUploader{
  trait Status
  case object EOF
  case object Uploading extends Status
  case object Completed extends Status
  case class Failed(reason: String) extends Status
  case class Chunk(envelopeId: String, fileId: String, bytes: ByteStream)

  def props(envelopeId: String, fileId: String, envelopeRepository: EnvelopeRepository): Props = Props(classOf[FileUploader], envelopeId, fileId, envelopeRepository)

	def parseBody(envelopeId: String, fileId: String) : BodyParser[String] = BodyParser{ request =>
		// FIXME we are not actually parsing the body; we are storing it in the DB
		// FIXME use an EssentialFilter instead to clarify our intent
		implicit val ec = Actors.actorSystem.dispatcher
		implicit val timeout = Timeout(500 millis)
		val fileUploader = Actors.actorSystem.actorOf(FileUploader.props(envelopeId, fileId, FileUploadActors.envelopeRepository))

		Iteratee.foreach[ByteStream]( fileUploader ! _ ).map{ _ =>
			Await.result((fileUploader ? FileUploader.EOF)
				.mapTo[FileUploader.Status]
				.map{
					case Completed => Right("upload successful")
					case Failed(reason) => Left(Results.InternalServerError)
				}, timeout.duration)
		}
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
      sink = sink.flatMap(itr => itr.feed(El(stream)))
    case EOF =>
      sink = sink.flatMap(itr => itr.feed(Input.EOF))
      status = Completed
      val replyTo = sender
      sink.onComplete{
        case Failure(NonFatal(e)) =>
          replyTo ! Failed(e.getMessage)
          self ! PoisonPill
        case Success(_) =>
          replyTo ! status
          self ! PoisonPill
      }

  }

  override def postStop = log.info(s"file upload request for $envelopeId complete")

}
