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
  location         : Option[DownloadUrl],
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

  val format: Format[FileTransferNotification] = {
    implicit val propertyFormat: Format[Property] =
      ( (__ \ "name" ).format[String]
      ~ (__ \ "value").format[String]
      )(Property.apply, p => Tuple.fromProductTyped(p))

    implicit val checksumFormat: Format[Checksum] =
      ( (__ \ "algorithm").format[String].inmap[Algorithm](unlift(Algorithm.apply), _.asString)
      ~ (__ \ "value"    ).format[String]
      )(Checksum.apply, cf => Tuple.fromProductTyped(cf))

    implicit val auditFormat: Format[Audit] =
      (__ \ "correlationID").format[String].inmap(Audit.apply, _.correlationId)

    implicit val downloadUrlFormat: Format[DownloadUrl] =
      implicitly[Format[String]].inmap[DownloadUrl](DownloadUrl.apply, _.value)

    implicit val fileFormat: Format[FileTransferFile] =
      ( (__ \ "recipientOrSender").format[String]
      ~ (__ \ "name"             ).format[String]
      ~ (__ \ "location"         ).formatNullable[DownloadUrl]
      ~ (__ \ "checksum"         ).format[Checksum]
      ~ (__ \ "size"             ).format[Int]
      ~ (__ \ "properties"       ).format[List[Property]]
      )(FileTransferFile.apply, ftf => Tuple.fromProductTyped(ftf))

    ( (__ \ "informationType").format[String]
    ~ (__ \ "file"           ).format[FileTransferFile]
    ~ (__ \ "audit"          ).format[Audit]
    )(FileTransferNotification.apply, ftn => Tuple.fromProductTyped(ftn))
  }
}

case class DownloadUrl(value: String) extends AnyVal {
  override def toString(): String = {
    //Do not log value
    "DownloadUrl(...)"
  }
}
