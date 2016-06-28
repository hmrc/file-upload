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

package uk.gov.hmrc.fileupload.controllers

import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc.{Result, RequestHeader, BodyParser}
import uk.gov.hmrc.fileupload.models.FileMetadata

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
	* Created by jay on 23/06/2016.
	*/
object FileMetadataParser extends BodyParser[FileMetadata]{

	def apply(request: RequestHeader): Iteratee[Array[Byte], Either[Result, FileMetadata]] = {
		import FileMetadata._

		Iteratee.consume[Array[Byte]]().map{ data =>
			Try(Json.fromJson[FileMetadata](Json.parse(data)).get) match {
				case Success(fileMetadata) => Right(fileMetadata)
				case Failure(NonFatal(e)) => Left(ExceptionHandler(e))
			}
		}(ExecutionContext.global)
	}
}
