/*
 * Copyright 2022 HM Revenue & Customs
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

import java.net.URL

import akka.stream.scaladsl.Source
import cats.syntax.either._
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.fileupload.infrastructure.{BasicAuth, EnvelopeConstraintsConfiguration}
import uk.gov.hmrc.fileupload.read.envelope.Service._
import uk.gov.hmrc.fileupload.read.envelope.{Envelope, EnvelopeStatus}
import uk.gov.hmrc.fileupload.read.stats.Stats.GetInProgressFileResult
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.write.infrastructure._
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId, FileId, FileRefId, read}
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class EnvelopeController @Inject()(
  appModule: ApplicationModule,
  cc: ControllerComponents
)(implicit executionContext: ExecutionContext
) extends BackendController(cc) {

  private val logger = Logger(getClass)

  val withBasicAuth: BasicAuth = appModule.withBasicAuth
  val nextId: () => EnvelopeId = appModule.nextId
  val handleCommand: (EnvelopeCommand) => Future[Either[CommandNotAccepted, CommandAccepted.type]] = appModule.envelopeCommandHandler
  val findEnvelope: EnvelopeId => Future[Either[FindError, Envelope]] = appModule.findEnvelope
  val findMetadata: (EnvelopeId, FileId) => Future[Either[FindMetadataError, read.envelope.File]] = appModule.findMetadata
  val findAllInProgressFile: () => Future[GetInProgressFileResult] = appModule.allInProgressFile
  val deleteInProgressFile: (FileRefId) => Future[Boolean] = appModule.deleteInProgressFile
  val getEnvelopesByStatus: (List[EnvelopeStatus], Boolean) => Source[Envelope, akka.NotUsed] = appModule.getEnvelopesByStatus
  val envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration = appModule.envelopeConstraintsConfigure

  import EnvelopeConstraintsConfiguration.{validateExpiryDate, durationsToDateTime}

  def create() = Action.async(parse.json[CreateEnvelopeRequest]) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(id) }"

    val result = for {
      envelopeConstraints <- validatedEnvelopeFilesConstraints(request).right
      expiryTimes = durationsToDateTime(envelopeConstraintsConfigure.defaultExpirationDuration, envelopeConstraintsConfigure.maxExpirationDuration)
      userExpiryDate = request.body.expiryDate.orElse(Some(expiryTimes.default))
      _ <- validateExpiryDate(expiryTimes.now, expiryTimes.max, userExpiryDate.get).right
      _ <- validateCallbackUrl(request, envelopeConstraintsConfigure)
    } yield {
      val command = CreateEnvelope(nextId(), request.body.callbackUrl, userExpiryDate, request.body.metadata, Some(envelopeConstraints))
      val userAgent = request.headers.get("User-Agent").getOrElse("none")
      logger.info(s"""envelopeId=${command.id} User-Agent=$userAgent""")
      handleCreate(envelopeLocation, command)
    }

    result match {
      case Right(successfulResult) =>
        successfulResult
      case Left(error) =>
        logger.warn(s"Validation error. user time: ${error}, user-agent: ${request.headers.get("User-Agent")}")
        Future.successful(BadRequestHandler(new BadRequestException(s"${error.message}")))
    }
  }

  private def validateCallbackUrl(
    request: Request[CreateEnvelopeRequest],
    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration
  ): Either[InvalidCallbackUrl, Unit] =
    if (envelopeConstraintsConfigure.enforceHttps) {
      val allowedProtocols = Seq("https")

      request.body.callbackUrl match {
        case None => Right(())
        case Some(url) =>
          for {
            parsedUrl <- Either.catchNonFatal(new URL(url)).left.map(_ => InvalidCallbackUrl(url))
            _ <- if (allowedProtocols.contains(parsedUrl.getProtocol)) Right(()) else Left(InvalidCallbackUrl(url))
          } yield ()
      }
    } else
      Right(())

  def createWithId(id: EnvelopeId) = Action.async(parse.json[CreateEnvelopeRequest]) { implicit request =>
    def envelopeLocation = (id: EnvelopeId) => LOCATION -> s"${ request.host }${ uk.gov.hmrc.fileupload.controllers.routes.EnvelopeController.show(id) }"

    val validatedUserEnvelopeConstraints = validatedEnvelopeFilesConstraints(request)

    validatedUserEnvelopeConstraints match {
      case Right(envelopeConstraints: EnvelopeFilesConstraints) ⇒
        val command = CreateEnvelope(id, request.body.callbackUrl, request.body.expiryDate, request.body.metadata, Some(envelopeConstraints))
        val userAgent = request.headers.get("User-Agent").getOrElse("none")
        logger.info(s"""envelopeId=${command.id} User-Agent=$userAgent""")
        handleCreate(envelopeLocation, command)
      case Left(failureReason: ConstraintsValidationFailure) ⇒
        Future.successful(BadRequestHandler(new BadRequestException(s"${failureReason.message}")))
    }
  }


  private def handleCreate(envelopeLocation: EnvelopeId => (String, String), command: CreateEnvelope): Future[Result] = {
    handleCommand(command).map {
      case Right(_) => Created.withHeaders(envelopeLocation(command.id))
      case Left(EnvelopeAlreadyCreatedError) => ExceptionHandler(BAD_REQUEST, "Envelope already created")
      case Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Left(error) => ExceptionHandler(BAD_REQUEST, s"Envelope not created due to: $error")
    }.recover { case e => ExceptionHandler(e) }
  }

  def delete(id: EnvelopeId) = Action.async { implicit request =>
    logger.debug(s"delete: EnvelopeId=$id")

    withBasicAuth {
      handleCommand(DeleteEnvelope(id)).map {
        case Right(_) => Ok
        case Left(EnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
        case Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
        case Left(_) => ExceptionHandler(BAD_REQUEST, "Envelope not deleted")
      }.recover { case e => ExceptionHandler(e) }
    }
  }

  def deleteFile(id: EnvelopeId, fileId: FileId) = Action.async { request =>
    logger.debug(s"deleteFile: EnvelopeId=$id fileId=$fileId")

    handleCommand(DeleteFile(id, fileId)).map {
      case Right(_) => Ok
      case Left(FileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
      case Left(CommandError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
      case Left(EnvelopeRoutingAlreadyRequestedError | EnvelopeSealedError) =>
        ExceptionHandler(LOCKED, s"File not deleted, as routing request already received for envelope: $id sealed")
      case Left(_) => ExceptionHandler(BAD_REQUEST, "File not deleted")
    }.recover { case e => ExceptionHandler(e) }
  }

  def show(id: EnvelopeId) = Action.async {
    import EnvelopeReport._
    logger.debug(s"show: EnvelopeId=$id")

    findEnvelope(id).map {
      case Right(e) => Ok(Json.toJson(fromEnvelope(e)))
      case Left(FindEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
      case Left(FindServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
  }

  def list(getEnvelopesByStatusQuery: GetEnvelopesByStatus) = Action {
    logger.debug(s"list by status")
    import EnvelopeReport._
    Ok.chunked(
      getEnvelopesByStatus(getEnvelopesByStatusQuery.status, getEnvelopesByStatusQuery.inclusive)
        .map(e => Json.toJson(fromEnvelope(e)))
    )
  }

  def retrieveMetadata(id: EnvelopeId, fileId: FileId) = Action.async { request =>
    logger.debug(s"retrieveMetadata: envelopeId=$id fileId=$fileId")
    import GetFileMetadataReport._

    findMetadata(id, fileId).map {
      case Right(f) => Ok(Json.toJson(fromFile(id, f)))
      case Left(FindMetadataEnvelopeNotFoundError) => ExceptionHandler(NOT_FOUND, s"Envelope with id: $id not found")
      case Left(FindMetadataFileNotFoundError) => ExceptionHandler(NOT_FOUND, s"File with id: $fileId not found in envelope: $id")
      case Left(FindMetadataServiceError(m)) => ExceptionHandler(INTERNAL_SERVER_ERROR, m)
    }.recover { case e => ExceptionHandler(e) }
  }

  def inProgressFiles() = Action.async {
    findAllInProgressFile().map {
      case Right(inProgressFiles) => Ok(Json.toJson(inProgressFiles))
      case Left(error) => InternalServerError("It was not possible to retrieve in progress files")
    }
  }

  def deleteInProgressFileByRefId(fileRefId: FileRefId) = Action.async {
    deleteInProgressFile(fileRefId).map {
      case true => Ok
      case false => InternalServerError("It was not possible to delete the in progress file")
    }.recover { case e => ExceptionHandler(e) }
  }

  private def validatedEnvelopeFilesConstraints(request: Request[CreateEnvelopeRequest]): Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
    EnvelopeConstraintsConfiguration
      .validateEnvelopeFilesConstraints(request.body.constraints.getOrElse(EnvelopeConstraintsUserSetting()),
        envelopeConstraintsConfigure)
}
