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
}

case class File(
  recipientOrSender: Option[String],
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
  file           : File,
  audit          : Audit
)

object FileTransferNotification {

  val writes = {
    implicit val propertyWrites =
      ( (__ \ "name" ).write[String]
      ~ (__ \ "value").write[String]
      )(unlift(Property.unapply))

    implicit val checksumWrites =
      ( (__ \ "algorithm").write[String].contramap[Algorithm](_.asString)
      ~ (__ \ "value"    ).write[String]
      )(unlift(Checksum.unapply))

    implicit val auditWrites =
      (__ \ "correlationID").write[String].contramap(unlift(Audit.unapply))

    implicit val fileWrites =
      ( (__ \ "recipientOrSender").writeNullable[String]
      ~ (__ \ "name"             ).write[String]
      ~ (__ \ "location"         ).writeNullable[String]
      ~ (__ \ "checksum"         ).write[Checksum]
      ~ (__ \ "size"             ).write[Int]
      ~ (__ \ "properties"       ).write[List[Property]]
      )(unlift(File.unapply))

    ( (__ \ "informationType").write[String]
    ~ (__ \ "file"           ).write[File]
    ~ (__ \ "audit"          ).write[Audit]
    )(unlift(FileTransferNotification.unapply))
  }
}
