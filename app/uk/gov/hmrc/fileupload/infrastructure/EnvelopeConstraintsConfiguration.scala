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

package uk.gov.hmrc.fileupload.infrastructure

import java.time.Duration

import org.joda.time.DateTime
import play.api.Configuration
import uk.gov.hmrc.fileupload.controllers._

object EnvelopeConstraintsConfiguration:

  private def throwRuntimeException(key: String): Nothing =
    throw RuntimeException(s"default value $key need to define correctly")

  def checkOptionIntValue(data: Option[Int], position: String): Int =
    data match
      case Some(num: Int) if num < 1 => throwRuntimeException(s"$position")
      case Some(num: Int)            => num
      case _                         => throwRuntimeException(s"$position")

  def getEnvelopeConstraintsConfiguration(runModeConfiguration: Configuration): Either[ConstraintsValidationFailure, EnvelopeConstraintsConfiguration] =
    val maxExpiryDuration = runModeConfiguration.underlying.getDuration("constraints.maxExpiryDuration")
    val defaultExpiryDuration = runModeConfiguration.underlying.getDuration("constraints.defaultExpiryDuration")

    val times = durationsToDateTime(defaultExpiryDuration, maxExpiryDuration)

    def getFileConstraintsFromConfig(keyPrefix: String) =
      val acceptedMaxItems: Int =
        checkOptionIntValue(runModeConfiguration.getOptional[Int](s"$keyPrefix.maxItems"), s"$keyPrefix.maxItems")

      val acceptedMaxSize: String =
        runModeConfiguration.getOptional[String](s"$keyPrefix.maxSize").getOrElse(throwRuntimeException(s"$keyPrefix.maxSize"))

      val acceptedMaxSizePerItem: String =
        runModeConfiguration
          .getOptional[String](s"$keyPrefix.maxSizePerItem").getOrElse(throwRuntimeException(s"$keyPrefix.maxSizePerItem"))


      val acceptedAllowZeroLengthFiles =
        runModeConfiguration.getOptional[Boolean](s"$keyPrefix.allowZeroLengthFiles")

      for
        acceptedMaxSize        <- Size(acceptedMaxSize)
        acceptedMaxSizePerItem <- Size(acceptedMaxSizePerItem)
      yield EnvelopeFilesConstraints(
        maxItems             = acceptedMaxItems,
        maxSize              = acceptedMaxSize,
        maxSizePerItem       = acceptedMaxSizePerItem,
        allowZeroLengthFiles = acceptedAllowZeroLengthFiles
      )

    val acceptedEnvelopeConstraints: Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
      getFileConstraintsFromConfig(keyPrefix = "constraints.accepted")

    val defaultEnvelopeConstraints: Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
      getFileConstraintsFromConfig(keyPrefix = "constraints.default")

    val enforceHttps = runModeConfiguration.getOptional[Boolean]("constraints.enforceHttps").getOrElse(false)

    validateExpiryDate(times.now, times.max, times.default) match
      case Right(_) =>
        for
          accepted <- acceptedEnvelopeConstraints
          default  <- defaultEnvelopeConstraints
        yield EnvelopeConstraintsConfiguration(
          acceptedEnvelopeConstraints = accepted,
          defaultEnvelopeConstraints = default,
          maxExpiryDuration,
          defaultExpiryDuration,
          enforceHttps
        )
      case Left(error) =>
        Left(error)

  def validateEnvelopeFilesConstraints(
    constraintsO                : EnvelopeConstraintsUserSetting,
    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration
): Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
    val defaultEnvelopeConstraints  = envelopeConstraintsConfigure.defaultEnvelopeConstraints
    val acceptedEnvelopeConstraints = envelopeConstraintsConfigure.acceptedEnvelopeConstraints

    val maxItems = constraintsO.maxItems.getOrElse(defaultEnvelopeConstraints.maxItems)

    val userEnvelopeConstraints =
      for
        userMaxSize        <- Size(constraintsO.maxSize.getOrElse(defaultEnvelopeConstraints.maxSize.toString))
        userMaxSizePerItem <- Size(constraintsO.maxSizePerItem.getOrElse(defaultEnvelopeConstraints.maxSizePerItem.toString))
      yield EnvelopeFilesConstraints(
        maxItems             = maxItems,
        maxSize              = userMaxSize,
        maxSizePerItem       = userMaxSizePerItem,
        allowZeroLengthFiles = constraintsO.allowZeroLengthFiles.orElse(defaultEnvelopeConstraints.allowZeroLengthFiles)
      )

    for {
      useConstraints <- userEnvelopeConstraints
    } {
      val maxSizeInBytes       : Long = useConstraints.maxSizeInBytes
      val maxSizePerItemInBytes: Long = useConstraints.maxSizePerItemInBytes

      val acceptedMaxSizeInBytes       : Long = acceptedEnvelopeConstraints.maxSize.inBytes
      val acceptedMaxSizePerItemInBytes: Long = acceptedEnvelopeConstraints.maxSizePerItem.inBytes

      require(!(maxSizeInBytes > acceptedMaxSizeInBytes),
        s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${acceptedEnvelopeConstraints.maxSize}")

      require(!(maxSizePerItemInBytes > acceptedMaxSizePerItemInBytes),
        s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${acceptedEnvelopeConstraints.maxSizePerItem}")

      require(maxSizeInBytes >= maxSizePerItemInBytes,
        s"constraints.maxSizePerItem can not greater than constraints.maxSize")
    }

    userEnvelopeConstraints

  def validateExpiryDate(now: DateTime, max: DateTime, userExpiryDate: DateTime): Either[InvalidExpiryDate.type, Unit] =
    if now.isAfter(userExpiryDate) || userExpiryDate.isAfter(max) then
      Left(InvalidExpiryDate)
    else
      Right(())

  def durationsToDateTime(default: Duration, max: Duration): ExpiryTimes =
    val now = DateTime.now()
    ExpiryTimes(
      default = now.plus(default.toMillis),
      max     = now.plus(max.toMillis),
      now
    )

  case class ExpiryTimes(
    default: DateTime,
    max    : DateTime,
    now    : DateTime
  )

end EnvelopeConstraintsConfiguration

case class EnvelopeConstraintsConfiguration(
  acceptedEnvelopeConstraints: EnvelopeFilesConstraints,
  defaultEnvelopeConstraints : EnvelopeFilesConstraints,
  maxExpirationDuration      : Duration,
  defaultExpirationDuration  : Duration,
  enforceHttps               : Boolean
)
