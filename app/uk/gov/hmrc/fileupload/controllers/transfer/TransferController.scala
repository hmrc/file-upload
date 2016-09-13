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

package uk.gov.hmrc.fileupload.controllers.transfer

import controllers.Assets
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.controllers.ExceptionHandler
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, OutputForTransfer}
import uk.gov.hmrc.fileupload.write.envelope.{ArchiveEnvelope, EnvelopeCommand}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class TransferController(getEnvelopesByDestination: Option[String] => Future[List[Envelope]],
                         handleCommand: (EnvelopeCommand) => Future[Boolean])
                        (implicit executionContext: ExecutionContext) extends BaseController {

  def list() = Action.async { implicit request =>
    val result =
      s"""
         |{
         |  "_links": {
         |    "self": {
         |      "href": "http://full.url.com/file-routing/envelope?destination=DMS"
         |    }
         |  },
         |  "_embedded": {
         |    "envelopes": [
         |      {
         |        "id": "0b215e97-11d4-4006-91db-c067e74fc653",
         |        "destination": "DMS",
         |        "application": "application:digital.forms.service/v1.233",
         |        "_embedded": {
         |          "files": [
         |            {
         |              "href": "/file-upload/envelope/b215e97-11d4-4006-91db-c067e74fc653/file/1/content",
         |              "name": "original-file-name-on-disk.docx",
         |              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
         |              "length": 1231222,
         |              "created": "2016-03-31T12:33:45Z",
         |              "_links": {
         |                "self": {
         |                  "href": "/file-upload/envelope/b215e97-11d4-4006-91db-c067e74fc653/file/1"
         |                }
         |              }
         |            },
         |            {
         |              "href": "/file-upload/envelope/b215e97-11d4-4006-91db-c067e74fc653/file/2/content",
         |              "name": "another-file-name-on-disk.docx",
         |              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
         |              "length": 112221,
         |              "created": "2016-03-31T12:33:45Z",
         |              "_links": {
         |                "self": {
         |                  "href": "/file-upload/envelope/b215e97-11d4-4006-91db-c067e74fc653/file/2"
         |                }
         |              }
         |            }
         |          ]
         |        },
         |        "_links": {
         |          "self": {
         |            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653"
         |          },
         |          "package": {
         |            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653",
         |            "type": "application/zip"
         |          },
         |          "files": [
         |            {
         |              "href": "/file/1",
         |              "href": "/file/2"
         |            }
         |          ]
         |        }
         |      }
         |    ]
         |  }
         |}
       """.stripMargin

    Future.successful(Ok(Json.parse(result)))
  }

  def download(envelopeId: EnvelopeId): Action[AnyContent] =
    Assets.at(path="/public", file="transfer/envelope.zip")

  def delete(envelopeId: EnvelopeId) = Action.async { implicit request =>
    Future.successful(Ok)
  }

  def nonStubDelete(envelopeId: EnvelopeId) = Action.async { implicit request =>
    val command = ArchiveEnvelope(envelopeId)

    handleCommand(command).map {
      case true => Ok
      case false => ExceptionHandler(INTERNAL_SERVER_ERROR, s"Envelope with id: $envelopeId not deleted")
    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }

//    softDelete(envelopeId).map {
//      case Xor.Right(_) => Ok
//      case Xor.Left(SoftDeleteServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
//      case Xor.Left(SoftDeleteEnvelopeNotFound) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $envelopeId not found")
//      case Xor.Left(SoftDeleteEnvelopeAlreadyDeleted) => ExceptionHandler(GONE, s"Envelope with id: $envelopeId already deleted")
//      case Xor.Left(SoftDeleteEnvelopeInWrongState) => ExceptionHandler(LOCKED, s"Envelope with id: $envelopeId locked")
//    }.recover { case e => ExceptionHandler(SERVICE_UNAVAILABLE, e.getMessage) }
  }

  def nonStubList() = Action.async { implicit request =>
    val maybeDestination = request.getQueryString("destination")
    getEnvelopesByDestination(maybeDestination).map { envelopes =>
      Ok(OutputForTransfer(envelopes))
    }
  }
}
