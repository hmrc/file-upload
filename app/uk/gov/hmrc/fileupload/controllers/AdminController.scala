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

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.read.file.FileInfo
import uk.gov.hmrc.fileupload.utils.errorAsJson
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.control.NonFatal

class AdminController(getFileInfo: (FileRefId) => Future[Option[FileInfo]], getChunks: (FileRefId) => Future[Int])
                     (implicit executionContext: ExecutionContext) extends BaseController {

  def retrieveFileMetaData(fileRefId: FileRefId) = Action.async { request =>
    getFileInfo(fileRefId).flatMap {
      case Some(f) => {
        val expectedNoChunks = math.ceil(f.length.toDouble / f.chunkSize)
        getChunks(fileRefId).map { actualNoChunks =>
          if (actualNoChunks == expectedNoChunks) {
            Ok(Json.toJson(f))
          } else {
            Ok(errorAsJson(s"Some file chunks are missing! Number of chunks expected $expectedNoChunks , actual $actualNoChunks"))
          }
        }.recover {
          case NonFatal(ex) =>
            Logger.warn(s"Retrieval of chunks for the file id $fileRefId failed ${ex.getMessage}")
            InternalServerError(ex.getMessage)
        }
      }
      case None => Future.successful(NotFound)
    }
  }
}
