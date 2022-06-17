/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mongodb.scala.bson.BsonDocument
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.transaction.{Transactions, TransactionConfiguration}

import scala.concurrent.{ExecutionContext, Future}

class LastSeenRepository(
  override val mongoComponent: MongoComponent
)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[EnvelopeId](
  collectionName = "lastSeen",
  mongoComponent = mongoComponent,
  domainFormat   = LastSeenRepository.envelopeIdFormat,
  indexes        = Seq.empty
)  with Transactions {
  private implicit val tc = TransactionConfiguration.strict

  def getLastSeen(): Future[Option[EnvelopeId]] =
    collection.find().headOption()

  def putLastSeen(envelopeId: EnvelopeId): Future[Unit] =
    withSessionAndTransaction { session =>
      for {
        _ <- collection.deleteMany(BsonDocument()).toFuture()
        _ <- collection.insertOne(envelopeId).toFuture()
      } yield ()
    }
}

object LastSeenRepository {
  val envelopeIdFormat: Format[EnvelopeId] =
    Format.at[String](__ \ "_id").inmap(EnvelopeId.apply, unlift(EnvelopeId.unapply))
}
