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

package uk.gov.hmrc.fileupload.controllers

import scala.util.matching.Regex

case class EnvelopeFilesConstraints(
  maxItems            : Int,
  maxSize             : Size,
  maxSizePerItem      : Size,
  allowZeroLengthFiles: Option[Boolean]
):
  val maxSizeInBytes: Long =
    maxSize.inBytes

  val maxSizePerItemInBytes: Long =
    maxSizePerItem.inBytes

enum SizeUnit:
  case KB extends SizeUnit
  case MB extends SizeUnit

case class Size(value: Long, unit: SizeUnit):
  override def toString: String =
    s"$value${unit.toString}"

  def inBytes: Long =
    unit match
      case SizeUnit.KB => value * 1024
      case SizeUnit.MB => value * 1024 * 1024

object Size {

  val sizeRegex: Regex = "([1-9][0-9]{0,3})([KB,MB]{2})".r

  def apply(asString: String): Either[ConstraintsValidationFailure, Size] =
    if asString.isEmpty then
      Left(EmptyInput)
    else
      asString.toUpperCase match
        case sizeRegex(num, unit) =>
          unit match
            case "KB" => Right(Size(num.toInt, SizeUnit.KB))
            case "MB" => Right(Size(num.toInt, SizeUnit.MB))
            case _    => Left(InvalidFormat)
        case _ => Left(InvalidFormat)
}

sealed trait ConstraintsValidationFailure {
  def message: String
}

case object EmptyInput extends ConstraintsValidationFailure {
  override def message: String = "input was empty"
}

case object InvalidFormat extends ConstraintsValidationFailure {
  override def message: String = s"input did not match supported size format, 'KB' and 'MB' are supported, e.g. 10MB"
}

case object InvalidExpiryDate extends ConstraintsValidationFailure {
  override def message: String = s"expiry date is not valid. It should be after now and before the max limit"
}

case class InvalidCallbackUrl(url : String) extends ConstraintsValidationFailure {
  override def message: String = s"invalid callback URL [$url]"
}
