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

import uk.gov.hmrc.fileupload.write.envelope.EnvelopeHandler.ContentTypes

case class EnvelopeConstraints (maxItems: Int,
                                maxSize: Size,
                                maxSizePerItem: Size,
                                contentTypes: List[ContentTypes]) {

  val maxSizeInBytes: Long = maxSize.inBytes
  val maxSizePerItemInBytes: Long = maxSizePerItem.inBytes
}

sealed trait SizeUnit
case object KB extends SizeUnit
case object MB extends SizeUnit

case class Size(value: Long, unit: SizeUnit) {
  override def toString: String = value + unit.toString

  def inBytes: Long = {
    unit match {
      case KB => value * 1024
      case MB => value * 1024 * 1024
    }
  }
}

object Size {

  val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r

  def apply(asString: String) = {
    if (asString.isEmpty) throw new IllegalArgumentException(s"Invalid constraint input")
    else {
      asString.toUpperCase match {
        case sizeRegex(num, unit) =>
          unit match {
            case "KB" => new Size(num.toInt, KB)
            case "MB" => new Size(num.toInt, MB)
            case _ => throw new IllegalArgumentException(s"Invalid constraint input")
          }
        case _ => throw new IllegalArgumentException(s"Invalid constraint input")
      }
    }
  }



}
