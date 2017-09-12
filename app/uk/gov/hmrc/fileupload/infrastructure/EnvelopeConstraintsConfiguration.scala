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
import uk.gov.hmrc.fileupload.controllers.{EnvelopeConstraints, EnvelopeConstraintsUserSetting, Size}
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
      case Some(num:Int) ⇒
        if (num<1) throwRuntimeException(s"$position")
        else num
      case _ ⇒ throwRuntimeException(s"$position")
    }
  }

  def getEnvelopeConstraintsConfiguration(runModeConfiguration: Configuration): EnvelopeConstraintsConfiguration = {
    val acceptedMaxItems: Int = checkOptionIntValue(runModeConfiguration.getInt("constraints.accepted.maxItems"), "accepted.maxItems")

    val acceptedMaxSize: String = runModeConfiguration.getString("constraints.accepted.maxSize").getOrElse(throwRuntimeException("accepted.maxSize"))

    val acceptedMaxSizePerItem: String = runModeConfiguration
                                          .getString("constraints.accepted.maxSizePerItem").getOrElse(throwRuntimeException("accepted.maxSizePerItem"))

    val acceptedContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString("constraints.accepted.contentTypes"))
                                                    .getOrElse(throwRuntimeException("accepted.contentTypes"))

    val defaultMaxItems: Int = checkOptionIntValue(runModeConfiguration.getInt("constraints.default.maxItems"), "default.maxItems")

    val defaultMaxSize: String = runModeConfiguration.getString("constraints.default.maxSize").getOrElse(throwRuntimeException("default.maxSize"))

    val defaultMaxSizePerItem: String = runModeConfiguration
                                          .getString("constraints.default.maxSizePerItem").getOrElse(throwRuntimeException("default.maxSizePerItem"))

    val defaultContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString("constraints.default.contentTypes"))
                                                    .getOrElse(throwRuntimeException("default.contentTypes"))

    EnvelopeConstraintsConfiguration(acceptedMaxItems = acceptedMaxItems,
                                     acceptedMaxSize = Size(acceptedMaxSize),
                                     acceptedMaxSizePerItem = Size(acceptedMaxSizePerItem),
                                     acceptedContentTypes = acceptedContentTypes,
                                     defaultMaxItems = defaultMaxItems,
                                     defaultMaxSize = Size(defaultMaxSize),
                                     defaultMaxSizePerItem = Size(defaultMaxSizePerItem),
                                     defaultContentTypes = defaultContentTypes)
  }

  def formatUserEnvelopeConstraints(constraintsO: EnvelopeConstraintsUserSetting,
                                    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration): Option[EnvelopeConstraints] = {

    val maxItems = constraintsO.maxItems.getOrElse(envelopeConstraintsConfigure.defaultMaxItems)
    val maxSize = Size(constraintsO.maxSize.getOrElse(envelopeConstraintsConfigure.defaultMaxSize.toString))
    val maxSizePerItem = Size(constraintsO.maxSizePerItem.getOrElse(envelopeConstraintsConfigure.defaultMaxSizePerItem.toString))
    val contentTypes = checkContentTypes(constraintsO.contentTypes.getOrElse(envelopeConstraintsConfigure.defaultContentTypes),
                                         envelopeConstraintsConfigure.defaultContentTypes)

    val maxSizeInBytes: Long = maxSize.inBytes
    val maxSizePerItemInBytes: Long = maxSizePerItem.inBytes

    val acceptedMaxSizeInBytes: Long = envelopeConstraintsConfigure.acceptedMaxSize.inBytes
    val acceptedMaxSizePerItemInBytes: Long = envelopeConstraintsConfigure.acceptedMaxSizePerItem.inBytes

    require(!(maxSizeInBytes > acceptedMaxSizeInBytes),
      s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSize}")
    require(!(maxSizePerItemInBytes > acceptedMaxSizePerItemInBytes),
      s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSizePerItem}")
    require(maxSizeInBytes>=maxSizePerItemInBytes,
      s"constraints.maxSizePerItem can not greater than constraints.maxSize")

    Some(EnvelopeConstraints(maxItems = maxItems,
         maxSize = maxSize,
         maxSizePerItem = maxSizePerItem,
         contentTypes = contentTypes
        ) )
  }

  def checkContentTypes(contentTypes: List[ContentTypes], defaultContentTypes: List[ContentTypes]): List[ContentTypes] = {
    if (contentTypes.isEmpty) defaultContentTypes
    else contentTypes
  }

}

case class EnvelopeConstraintsConfiguration(acceptedMaxItems:Int,
                                            acceptedMaxSize: Size,
                                            acceptedMaxSizePerItem: Size,
                                            acceptedContentTypes: List[ContentTypes],
                                            defaultMaxItems: Int,
                                            defaultMaxSize: Size,
                                            defaultMaxSizePerItem: Size,
                                            defaultContentTypes: List[ContentTypes])
