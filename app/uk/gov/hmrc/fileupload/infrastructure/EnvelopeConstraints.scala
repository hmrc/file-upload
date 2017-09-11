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
import uk.gov.hmrc.fileupload.controllers.EnvelopeConstraintsUserSetting
import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes

object EnvelopeConstraints {

  private val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r

  def isAValidSize(size: String): Boolean = {
    if (size.isEmpty) false
    else {
      size.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => true
            case "MB" => true
            case _ => false
          }
        case _ => false
      }
    }
  }

  def translateToByteSize(size: String): Long = {
    if (!isAValidSize(size)) throw new IllegalArgumentException(s"Invalid constraint input")
    else {
      size.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => num.toInt * 1024
            case "MB" => num.toInt * 1024 * 1024
          }
      }
    }
  }

  def throwRuntimeException(key: String): Nothing = {
    throw new RuntimeException(s"default value $key need to define")
  }

  def getContentTypesInList(contentTypesInString: Option[String]): Option[List[ContentTypes]] = {
    contentTypesInString.map(types ⇒ types.split(",").toList)
  }

  def getEnvelopeConstraintsConfiguration(runModeConfiguration: Configuration): EnvelopeConstraintsConfiguration = {
    val acceptedMaxItems: Int = runModeConfiguration.getInt("constraints.accepted.maxItems")
      .getOrElse(throwRuntimeException("accepted.maxNumFiles"))
    val acceptedMaxSize: String = runModeConfiguration.getString("constraints.accepted.maxSize")
      .getOrElse(throwRuntimeException("accepted.maxMaxSize"))
    val acceptedMaxSizePerItem: String = runModeConfiguration.getString("constraints.accepted.maxSizePerItem")
      .getOrElse(throwRuntimeException("accepted.maxSizePerItem"))
    val acceptedContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString("constraints.accepted.contentTypes"))
      .getOrElse(throwRuntimeException("accepted.contentTypes"))

    val defaultMaxItems: Int = runModeConfiguration.getInt("constraints.default.maxItems")
      .getOrElse(throwRuntimeException("default.maxNumFiles"))
    val defaultMaxSize: String = runModeConfiguration.getString("constraints.default.maxSize")
      .getOrElse(throwRuntimeException("default.maxMaxSize"))
    val defaultMaxSizePerItem: String = runModeConfiguration.getString("constraints.default.maxSizePerItem")
      .getOrElse(throwRuntimeException("default.maxSizePerItem"))
    val defaultContentTypes: List[ContentTypes] = getContentTypesInList(runModeConfiguration.getString("constraints.default.contentTypes"))
      .getOrElse(throwRuntimeException("default.contentTypes"))

    EnvelopeConstraintsConfiguration(acceptedMaxItems = acceptedMaxItems,
                                     acceptedMaxSize = acceptedMaxSize,
                                     acceptedMaxSizePerItem = acceptedMaxSizePerItem,
                                     acceptedContentTypes = acceptedContentTypes,
                                     defaultMaxItems = defaultMaxItems,
                                     defaultMaxSize = defaultMaxSize,
                                     defaultMaxSizePerItem = defaultMaxSizePerItem,
                                     defaultContentTypes = defaultContentTypes)
  }

  def formatUserEnvelopeConstraints(constraintsO: EnvelopeConstraintsUserSetting,
                                    envelopeConstraintsConfigure: EnvelopeConstraintsConfiguration): Option[EnvelopeConstraints] = {

    val maxItems = constraintsO.maxItems.getOrElse(envelopeConstraintsConfigure.defaultMaxItems)
    val maxSize = constraintsO.maxSize.getOrElse(envelopeConstraintsConfigure.defaultMaxSize)
    val maxSizePerItem = constraintsO.maxSizePerItem.getOrElse(envelopeConstraintsConfigure.defaultMaxSizePerItem)

    val maxSizeInBytes: Long = translateToByteSize(maxSize)
    val maxSizePerItemInBytes: Long = translateToByteSize(maxSizePerItem)

    val acceptedMaxSizeInBytes: Long = translateToByteSize(envelopeConstraintsConfigure.acceptedMaxSize)
    val acceptedMaxSizePerItemInBytes: Long = translateToByteSize(envelopeConstraintsConfigure.acceptedMaxSizePerItem)

    require(isAValidSize(maxSize),
      s"Input constraints.maxSize is not a valid input, e.g 250MB")
    require(isAValidSize(maxSizePerItem),
      s"Input constraints.maxSizePerItem is not a valid input, e.g 100MB")
    require(!(maxSizeInBytes > acceptedMaxSizeInBytes),
      s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSize}")
    require(!(maxSizePerItemInBytes > acceptedMaxSizePerItemInBytes),
      s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${envelopeConstraintsConfigure.acceptedMaxSizePerItem}")
    require(maxSizeInBytes>=maxSizePerItemInBytes,
      s"constraints.maxSizePerItem can not greater than constraints.maxSize")

    Some(EnvelopeConstraints(maxItems = maxItems,
                             maxSize = maxSize.toUpperCase(),
                             maxSizePerItem = maxSizePerItem.toUpperCase(),
                             contentTypes = checkContentTypes(constraintsO.contentTypes.getOrElse(envelopeConstraintsConfigure.defaultContentTypes),
                                                              envelopeConstraintsConfigure.defaultContentTypes)
         ) )
  }

  def checkContentTypes(contentTypes: List[ContentTypes], defaultContentTypes: List[ContentTypes]): List[ContentTypes] = {
    if (contentTypes.isEmpty) defaultContentTypes
    else contentTypes
  }
}

case class EnvelopeConstraints(maxItems: Int,
                               maxSize: String,
                               maxSizePerItem: String,
                               contentTypes: List[ContentTypes]) {
  import EnvelopeConstraints._

  val maxSizeInBytes: Long = translateToByteSize(maxSize)
  val maxSizePerItemInBytes: Long = translateToByteSize(maxSizePerItem)

}

case class EnvelopeConstraintsConfiguration(acceptedMaxItems:Int,
                                            acceptedMaxSize: String,
                                            acceptedMaxSizePerItem: String,
                                            acceptedContentTypes: List[ContentTypes],
                                            defaultMaxItems: Int,
                                            defaultMaxSize: String,
                                            defaultMaxSizePerItem: String,
                                            defaultContentTypes: List[ContentTypes])
