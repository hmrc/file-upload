/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.read.routing

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Property(
  name : String,
  value: String
)

case class Checksum(
  algorithm: Algorithm,
  value    : String
)

sealed trait Algorithm {
  def asString: String
}
object Algorithm {
  case object Md5  extends Algorithm { override val asString = "md5"  }
  case object Sha1 extends Algorithm { override val asString = "SHA1" }
  case object Sha2 extends Algorithm { override val asString = "SHA2" }

  val values: List[Algorithm] = List(Md5, Sha1, Sha2)

  def apply(s: String): Option[Algorithm] =
    values.find(_.asString == s)
}

case class FileTransferFile(
  recipientOrSender: String,
  name             : String,
  location         : Option[String],
  checksum         : Checksum,
  size             : Int,
  properties       : List[Property]
)

case class Audit(
  correlationId: String
)

case class FileTransferNotification(
  informationType: String,
  file           : FileTransferFile,
  audit          : Audit
)

object FileTransferNotification {

  val format = {
    implicit val propertyFormat =
      ( (__ \ "name" ).format[String]
      ~ (__ \ "value").format[String]
      )(Property.apply, unlift(Property.unapply))

    implicit val checksumFormat =
      ( (__ \ "algorithm").format[String].inmap[Algorithm](unlift(Algorithm.apply), _.asString)
      ~ (__ \ "value"    ).format[String]
      )(Checksum.apply, unlift(Checksum.unapply))

    implicit val auditFormat =
      (__ \ "correlationID").format[String].inmap(Audit.apply, unlift(Audit.unapply))

    implicit val fileFormat =
      ( (__ \ "recipientOrSender").format[String]
      ~ (__ \ "name"             ).format[String]
      ~ (__ \ "location"         ).formatNullable[String]
      ~ (__ \ "checksum"         ).format[Checksum]
      ~ (__ \ "size"             ).format[Int]
      ~ (__ \ "properties"       ).format[List[Property]]
      )(FileTransferFile.apply, unlift(FileTransferFile.unapply))

    ( (__ \ "informationType").format[String]
    ~ (__ \ "file"           ).format[FileTransferFile]
    ~ (__ \ "audit"          ).format[Audit]
    )(FileTransferNotification.apply, unlift(FileTransferNotification.unapply))
  }
}
