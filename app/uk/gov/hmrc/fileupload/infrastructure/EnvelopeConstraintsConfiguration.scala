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

  def getEnvelopeConstraintsConfiguration(runModeConfiguration: Configuration): Either[SizeValidationFailure, EnvelopeConstraintsConfiguration] = {
    val acceptedMaxItems: Int = checkOptionIntValue(runModeConfiguration.getInt("constraints.accepted.maxItems"), "accepted.maxItems")

    val acceptedMaxSize: String = runModeConfiguration.getString("constraints.accepted.maxSize").getOrElse(throwRuntimeException("accepted.maxSize"))

    val acceptedMaxSizePerItem: String = runModeConfiguration
      .getString("constraints.accepted.maxSizePerItem").getOrElse(throwRuntimeException("accepted.maxSizePerItem"))

    val defaultMaxItems: Int = checkOptionIntValue(runModeConfiguration.getInt("constraints.default.maxItems"), "default.maxItems")

    val defaultMaxSize: String = runModeConfiguration.getString("constraints.default.maxSize").getOrElse(throwRuntimeException("default.maxSize"))

    val defaultMaxSizePerItem: String = runModeConfiguration
      .getString("constraints.default.maxSizePerItem").getOrElse(throwRuntimeException("default.maxSizePerItem"))

    val defaultContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString("constraints.default.contentTypes"))
      .getOrElse(throwRuntimeException("default.contentTypes"))

    val acceptedEnvelopeConstraints: Either[SizeValidationFailure, EnvelopeConstraints] = {
      for {
        acceptedMaxSize ← Size(acceptedMaxSize).right
        acceptedMaxSizePerItem ← Size(acceptedMaxSizePerItem).right
      } yield EnvelopeConstraints(acceptedMaxItems, acceptedMaxSize, acceptedMaxSizePerItem, List())
    }

    val defaultEnvelopeConstraints: Either[SizeValidationFailure, EnvelopeConstraints] = {
      for {
        defaultMaxSize ← Size(defaultMaxSize).right
        defaultMaxSizePerItem ← Size(defaultMaxSizePerItem).right
      } yield EnvelopeConstraints(defaultMaxItems, defaultMaxSize, defaultMaxSizePerItem, defaultContentTypes)
    }

    for {
      accepted ← acceptedEnvelopeConstraints.right
      default ← defaultEnvelopeConstraints.right
    } yield EnvelopeConstraintsConfiguration(acceptedEnvelopeConstraints = accepted,
      defaultEnvelopeConstraints = default)
  }

  def formatUserEnvelopeConstraints(constraintsO: EnvelopeConstraintsUserSetting,
                                    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration): Either[SizeValidationFailure, EnvelopeConstraints] = {

    val defaultEnvelopeConstraints = envelopeConstraintsConfigure.defaultEnvelopeConstraints
    val acceptedEnvelopeConstraints = envelopeConstraintsConfigure.acceptedEnvelopeConstraints

    val maxItems = constraintsO.maxItems.getOrElse(defaultEnvelopeConstraints.maxItems)

    val userEnvelopeConstraints = {
      for {
        userMaxSize ← Size(constraintsO.maxSize.getOrElse(defaultEnvelopeConstraints.maxSize.toString)).right
        userMaxSizePerItem ← Size(constraintsO.maxSizePerItem.getOrElse(defaultEnvelopeConstraints.maxSizePerItem.toString)).right
      } yield EnvelopeConstraints(maxItems = maxItems,
                                  maxSize = userMaxSize,
                                  maxSizePerItem = userMaxSizePerItem,
                                  contentTypes = List())
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

}

case class EnvelopeConstraintsConfiguration(acceptedEnvelopeConstraints: EnvelopeConstraints,
                                            defaultEnvelopeConstraints: EnvelopeConstraints)