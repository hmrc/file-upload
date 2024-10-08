/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.envelope

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.controllers

object OutputForTransfer:
  def generateJson(envelopes: Seq[Envelope])(using RequestHeader): JsValue =
    Json.obj(
      _links -> Json.obj(
        self -> Json.obj(
          href -> URLs.envelopesPerDestination
        )
      ),
      _embedded -> Json.obj(
        "envelopes" -> envelopes.map(stringifyEnvelope)
      )
    )

  def stringifyEnvelope(e: Envelope): JsValue =
    Json.obj(
      id          -> e._id,
      destination -> e.destination,
      application -> e.application,
      _embedded   -> Json.obj(
                       files -> mapFiles(e.files)(stringifyFile(e,_))
                     ),
      _links      -> Json.obj(
                       self      -> Json.obj(href -> URLs.fileTransferEnvelope(e._id)),
                       "package" -> Json.obj(
                                      href -> URLs.fileTransferEnvelope(e._id),
                                      "type" -> "application/zip"
                                    ),
                       files     -> mapFiles(e.files)(file =>
                                      Json.obj(href -> URLs.fileRelativeToEnvelope(file, e._id))
                                    )
                     )
    )

  private def mapFiles[A](files: Option[Seq[File]])(f: File => A): Seq[A] =
    files.map(_.map(f)).getOrElse(List.empty[A])

  def stringifyFile(e: Envelope, f: File): JsValue =
    Json.obj(
      href        -> URLs.fileDownloadContent(e._id, f.fileId),
      name        -> f.name.map(_.value),
      contentType -> f.contentType,
      length      -> f.length,
      created     -> f.uploadDate.map(formatDateAsUtc),
      _links      -> Json.obj(self ->
                       Json.obj(href ->
                         URLs.fileUri(e._id, f.fileId)
                       )
                     )
    )

  private def formatDateAsUtc(date: DateTime) =
    date.toString("yyyy-MM-dd'T'HH:mm:ss'Z'")

  private object URLs:
    def envelopesPerDestination(using rh: RequestHeader): String =
      val destination = rh.getQueryString("destination").map(d => s"?destination=$d").getOrElse("")
      controllers.transfer.routes.TransferController.list.absoluteURL(rh.secure) + destination

    def fileTransferEnvelope(envelopeId: EnvelopeId): String =
      controllers.transfer.routes.TransferController.download(envelopeId).url

    def fileDownloadContent(envelopeId: EnvelopeId, fileId: FileId): String =
       controllers.routes.FileController.downloadFile(envelopeId, fileId).url

    def fileUri(envelopeId: EnvelopeId, fileId: FileId): String =
      controllers.routes.EnvelopeController.deleteFile(envelopeId, fileId).url

    def fileRelativeToEnvelope(file: File, envelopeId: EnvelopeId): String =
      val envelopeUrl =  controllers.routes.EnvelopeController.show(envelopeId).url
      controllers.routes.EnvelopeController.deleteFile(envelopeId, file.fileId).url.stripPrefix(envelopeUrl)

  end URLs

  private val _links      = "_links"
  private val self        = "self"
  private val href        = "href"
  private val _embedded   = "_embedded"
  private val id          = "id"
  private val destination = "destination"
  private val application = "application"
  private val files       = "files"
  private val name        = "name"
  private val contentType = "contentType"
  private val length      = "length"
  private val created     = "created"

end OutputForTransfer
