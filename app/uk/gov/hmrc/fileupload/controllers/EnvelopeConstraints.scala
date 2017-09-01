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

package uk.gov.hmrc.fileupload.controllers

import uk.gov.hmrc.fileupload.read.envelope.Envelope.ContentTypes
import uk.gov.hmrc.fileupload.read.envelope._

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
}

case class EnvelopeConstraints(maxItems: Int,
                               maxSize: String,
                               maxSizePerItem: String,
                               contentTypes: List[ContentTypes]) {
  import EnvelopeConstraints._
  require(isAValidSize(maxSize),
          s"Input for constraints.maxSize is not a valid input, and exceeds maximum allowed value of ${Envelope.acceptedConstraints.maxSize}")
  require(isAValidSize(maxSizePerItem),
          s"Input constraints.maxSizePerItem is not a valid input, and exceeds maximum allowed value of ${Envelope.acceptedConstraints.maxSizePerItem}")

  val maxSizeInBytes: Long = translateToByteSize(maxSize)
  val maxSizePerItemInBytes: Long = translateToByteSize(maxSizePerItem)
  require(maxSizeInBytes>=maxSizePerItemInBytes, s"constraints.maxSizePerItem can not greater than constraints.maxSize")
}
