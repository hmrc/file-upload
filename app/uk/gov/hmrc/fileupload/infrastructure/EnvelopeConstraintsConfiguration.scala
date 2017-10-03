/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes

object EnvelopeConstraintsConfiguration {

  private def throwRuntimeException(key: String): Nothing = {
    throw new RuntimeException(s"default value $key need to define correctly")
  }

  def getContentTypesInList(contentTypesInString: Option[String]): Option[List[ContentTypes]] = {
    contentTypesInString.map(types ⇒ types.split(",").toList)
  }

  def checkOptionIntValue(data: Option[Int], position: String): Int = {
    data match {
      case Some(num: Int) ⇒
        if (num < 1) throwRuntimeException(s"$position")
        else num
      case _ ⇒ throwRuntimeException(s"$position")
    }
  }

  def getEnvelopeConstraintsConfiguration(runModeConfiguration: Configuration): Either[ConstraintsValidationFailure, EnvelopeConstraintsConfiguration] = {

    val maxExpiryDuration = runModeConfiguration.underlying.getDuration("constraints.maxExpiryDuration")
    val defaultExpiryDuration = runModeConfiguration.underlying.getDuration("constraints.defaultExpiryDuration")

    val times = durationsToDateTime(defaultExpiryDuration, maxExpiryDuration)

    def getFileConstraintsFromConfig(keyPrefix: ContentTypes) = {
      val acceptedMaxItems: Int = checkOptionIntValue(runModeConfiguration.getInt(s"$keyPrefix.maxItems"), s"$keyPrefix.maxItems")

      val acceptedMaxSize: String = runModeConfiguration.getString(s"$keyPrefix.maxSize").getOrElse(throwRuntimeException(s"$keyPrefix.maxSize"))

      val acceptedMaxSizePerItem: String = runModeConfiguration
        .getString(s"$keyPrefix.maxSizePerItem").getOrElse(throwRuntimeException(s"$keyPrefix.maxSizePerItem"))

      val acceptedContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString(s"$keyPrefix.contentTypes"))
        .getOrElse(throwRuntimeException(s"$keyPrefix.contentTypes"))

      for {
        acceptedMaxSize ← Size(acceptedMaxSize).right
        acceptedMaxSizePerItem ← Size(acceptedMaxSizePerItem).right
      } yield EnvelopeFilesConstraints(acceptedMaxItems, acceptedMaxSize, acceptedMaxSizePerItem, acceptedContentTypes)
    }

    val acceptedEnvelopeConstraints: Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
      getFileConstraintsFromConfig(keyPrefix = "constraints.accepted")

    val defaultEnvelopeConstraints: Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] =
      getFileConstraintsFromConfig(keyPrefix = "constraints.default")

    validateExpiryDate(times.now, times.max, times.default) match {
      case None =>
        for {
          accepted ← acceptedEnvelopeConstraints.right
          default ← defaultEnvelopeConstraints.right
        } yield EnvelopeConstraintsConfiguration(acceptedEnvelopeConstraints = accepted,
          defaultEnvelopeConstraints = default,
          maxExpiryDuration, defaultExpiryDuration
        )
      case Some(error) => Left(error)
    }
  }

  def validateEnvelopeFilesConstraints(constraintsO: EnvelopeConstraintsUserSetting,
                                       envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration): Either[ConstraintsValidationFailure, EnvelopeFilesConstraints] = {

    val defaultEnvelopeConstraints = envelopeConstraintsConfigure.defaultEnvelopeConstraints
    val acceptedEnvelopeConstraints = envelopeConstraintsConfigure.acceptedEnvelopeConstraints

    val maxItems = constraintsO.maxItems.getOrElse(defaultEnvelopeConstraints.maxItems)

    val contentTypes = checkContentTypes(constraintsO.contentTypes.getOrElse(defaultEnvelopeConstraints.contentTypes),
      defaultEnvelopeConstraints.contentTypes)

    val userEnvelopeConstraints = {
      for {
        userMaxSize ← Size(constraintsO.maxSize.getOrElse(defaultEnvelopeConstraints.maxSize.toString)).right
        userMaxSizePerItem ← Size(constraintsO.maxSizePerItem.getOrElse(defaultEnvelopeConstraints.maxSizePerItem.toString)).right
      } yield EnvelopeFilesConstraints(maxItems = maxItems,
                                  maxSize = userMaxSize,
                                  maxSizePerItem = userMaxSizePerItem,
                                  contentTypes = contentTypes)
    }

    for {
      useConstraints ← userEnvelopeConstraints.right
    } {
      val maxSizeInBytes: Long = useConstraints.maxSizeInBytes
      val maxSizePerItemInBytes: Long = useConstraints.maxSizePerItemInBytes

      val acceptedMaxSizeInBytes: Long = acceptedEnvelopeConstraints.maxSize.inBytes
      val acceptedMaxSizePerItemInBytes: Long = acceptedEnvelopeConstraints.maxSizePerItem.inBytes

      require(!(maxSizeInBytes > acceptedMaxSizeInBytes),
        s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${acceptedEnvelopeConstraints.maxSize}")
      require(!(maxSizePerItemInBytes > acceptedMaxSizePerItemInBytes),
        s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${acceptedEnvelopeConstraints.maxSizePerItem}")
      require(maxSizeInBytes >= maxSizePerItemInBytes,
        s"constraints.maxSizePerItem can not greater than constraints.maxSize")
    }

    userEnvelopeConstraints
  }

  def checkContentTypes(contentTypes: List[ContentTypes], defaultContentTypes: List[ContentTypes]): List[ContentTypes] = {
    if (contentTypes.isEmpty) defaultContentTypes
    else contentTypes
  }

  def validateExpiryDate(now: DateTime, max: DateTime, userExpiryDate: DateTime): Option[InvalidExpiryDate.type] = {
    if(now.isAfter(userExpiryDate) || userExpiryDate.isAfter(max))
      Some(InvalidExpiryDate)
    else
      None
  }
  def durationsToDateTime(default: Duration, max: Duration): ExpiryTimes = {
    val now = DateTime.now()
    def expiryDateFromDuration(dur: Duration) = now.plus(dur.toMillis)
    def maxT = expiryDateFromDuration(max)
    def defaultT = expiryDateFromDuration(default)
    ExpiryTimes(defaultT, maxT, now)
  }
  case class ExpiryTimes(default: DateTime, max: DateTime, now: DateTime)
}

case class EnvelopeConstraintsConfiguration(acceptedEnvelopeConstraints: EnvelopeFilesConstraints,
                                            defaultEnvelopeConstraints: EnvelopeFilesConstraints,
                                            maxExpirationDuration: Duration,
                                            defaultExpirationDuration: Duration
                                           )