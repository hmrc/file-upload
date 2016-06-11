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

package uk.gov.hmrc.fileupload

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
	* Created by jay on 11/06/2016.
	*/
package object actors {

	object Implicits{

		implicit class FutureUtil(f: Future[Any]){

			/**
				* exceptions are not propagated from actors so they have to reply to sender with the exception
				* This method converts such replies into failures
				*
				* @param ec ExecutionContext
				* @return a failed future on throwable or the original future
				*/
			def breakOnFailure(implicit ec: ExecutionContext): Future[Any] = {
				f.flatMap{
					case e: Throwable => Future(throw e)
					case other => Future(other)
				}
			}

			/**
				* When using the ask pattern and a Try type is sent back and you want to avoid
				* doing this:
				*   f.onComplete{
				*     case Success(Success(x)) => ...
				*     case Success(Failure(t)) => ...
				*   }
				*   use this function to flatten the Future's Try
				* @param ec ExecutionContext
				* @return the result of the inner Try or the original future
				*/
			def flattenTry(implicit ec: ExecutionContext): Future[Any] = {
				f.flatMap{
					case Success(s) 		=> Future(s)
					case Failure(e)     => Future(throw e)
					case other          => Future(other)
				}
			}
		}
	}

}
