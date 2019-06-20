/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.file.zip

import java.security.MessageDigest

import org.apache.commons.codec.binary.Hex
import play.api.libs.iteratee.Enumeratee.CheckDone
import play.api.libs.iteratee._
import uk.gov.hmrc.fileupload.file.zip.Utils.Bytes

import scala.concurrent.ExecutionContext.Implicits.global


case class BytesWithMetadata(messageDigest : MessageDigest, length : Int, chunk : Utils.Bytes) {
  def checksum() = messageDigest.synchronized {
    Hex.encodeHexString(messageDigest.digest())
  }
}

object ByteStreamChecksumCalculator {

  val computeChecksum: Enumeratee[Bytes, BytesWithMetadata] =
    Enumeratee.scanLeft[Utils.Bytes](BytesWithMetadata(MessageDigest.getInstance("SHA-256"), 0, Array.emptyByteArray))(
    (previousTo: BytesWithMetadata, currentFrom: Utils.Bytes) => {
      previousTo.messageDigest.synchronized {
        previousTo.messageDigest.update(currentFrom)
      }
      BytesWithMetadata(previousTo.messageDigest, previousTo.length + currentFrom.length, currentFrom)
    }
  )

  trait OnLast[From] {
    def apply(onLast : From => Unit): Enumeratee[From, From]
  }

  def onLast[From]: OnLast[From] = new OnLast[From] {

    def apply(onLast : From => Unit): Enumeratee[From, From] = new CheckDone[From, From] {

      def step[A](last : Option[From])(k: K[From, A]): K[From, Iteratee[From, A]] = {

        case in @ Input.El(e) =>
          new CheckDone[From, From] { def continue[A](k: K[From, A]) = Cont(step(Some(e))(k)) } &> k(Input.El(e))

        case Input.Empty =>
          new CheckDone[From, From] { def continue[A](k: K[From, A]) = Cont(step(last)(k)) } &> k(Input.Empty)

        case Input.EOF =>
          last.foreach(onLast)
          Done(Cont(k), Input.EOF)

      }

      def continue[A](k: K[From, A]) = Cont(step(None)(k))
    }
  }

  def computeChecksum(afterComputed : BytesWithMetadata => Unit): Enumeratee[Utils.Bytes, Utils.Bytes] =
    computeChecksum compose onLast(afterComputed) compose Enumeratee.map(_.chunk)

}
