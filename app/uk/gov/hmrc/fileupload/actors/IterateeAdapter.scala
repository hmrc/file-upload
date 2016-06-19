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

import akka.actor.{PoisonPill, ActorRef}
import akka.util.Timeout
import play.api.libs.iteratee.{Done, Input, Step, Iteratee}
import play.api.mvc.{Results, Result}
import uk.gov.hmrc.fileupload.ByteStream
import scala.concurrent.duration._
import akka.pattern._
import scala.language.postfixOps

import scala.concurrent.{Await, ExecutionContext, Future}

object IterateeAdapter{
	def apply(handler: ActorRef): IterateeAdapter = new IterateeAdapter(handler)
}

class IterateeAdapter(handler: ActorRef) extends Iteratee[ByteStream, Either[Result, String]]{
	import FileUploader._

	implicit val timeout = Timeout(2 seconds)

	def fold[B](folder: (Step[ByteStream, Either[Result, String]]) => Future[B])(implicit ec: ExecutionContext): Future[B] =
		folder(Step.Cont{
			case Input.EOF =>
				val result: Iteratee[ByteStream, Either[Result, String]] = Await.result(handler ? EOF, 2 seconds) match {
					case Completed => Done(Right("upload successful"), Input.Empty)
					case Failed(reason) => Done(Left(Results.InternalServerError), Input.Empty)
				}
				handler ! PoisonPill
				result
			case Input.Empty => this
			case Input.El(chunk) =>
				handler ! chunk
				this
		})
}
