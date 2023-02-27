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

package uk.gov.hmrc.fileupload.file.zip

import play.api.libs.iteratee._

import scala.concurrent.{ExecutionContext, Future, Promise}

object Utils {

  type Bytes = Array[Byte]

  implicit class LittleInt(i: Int) {
    def littleInt = Array[Byte](
      (i & 0xff).asInstanceOf[Byte],
      (i >> 8 & 0xff).asInstanceOf[Byte],
      (i >> 16 & 0xff).asInstanceOf[Byte],
      (i >> 24 & 0xff).asInstanceOf[Byte]
    )

    def littleShort = Array[Byte](
      (i & 0xff).asInstanceOf[Byte],
      (i >> 8 & 0xff).asInstanceOf[Byte]
    )

    def littleByte = Array[Byte]((i & 0xff).asInstanceOf[Byte])
  }

  implicit class EnumeratorUtils[E](en: Enumerator[E]) {
    def fold[S](state: S)(f: (S, E) => S)(implicit executionContext: ExecutionContext): (Enumerator[E], Future[S]) = {
      var st = state

      val folder = Enumeratee.map[E] { data =>
        st = f(st, data)
        data
      }

      val endStateP = Promise[S]()

      val onEof = Enumeratee.onEOF[E] { () =>
        endStateP.success(st)
      }

      (en &> folder &> onEof, endStateP.future)
    }

    def foldAndThen[S](state: S)(f: (S, E) => S)(endF: S => Enumerator[E])(implicit executionContext: ExecutionContext): Enumerator[E] = {
      val (foldedEn, endStateF) = fold(state)(f)

      val endEn = Enumerator.flatten(endStateF.map(endF))

      foldedEn >>> endEn
    }
  }

}
