/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.actors

import org.junit.Assert
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.{Support, actors}
import uk.gov.hmrc.fileupload.models.Envelope

import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import Assert._

/**
	* Created by jay on 08/06/2016.
	*/
class MarshallerSpec extends ActorSpec{

	import Support._
	import Marshaller._
	import Envelope._
	import scala.concurrent.duration._
	import akka.pattern._
	import actors.Implicits._

	val marshaller = system.actorOf(Marshaller.props)
	val blockingExc = new BlockingExecutionContext()

	"A Marshaller" should {
		"marshall a domain object into json" in {
			within(500 millis) {
				val envelope = Support.envelope
				val json = Json.toJson[Envelope](envelope)

				marshaller ! Marshall(envelope)
				expectMsg(Success(json))
			}
		}
		"unmarshall a json object to a domain object" in {
			within(500 millis) {
				val envelope = Support.envelope
				val json = Json.toJson[Envelope](envelope)

				marshaller ! UnMarshall(json, classOf[Envelope])

				expectMsg(Success(envelope))
			}
		}
		"unmarshall of an invalid json should fail" in {
			within(500 seconds){
				implicit val ec = blockingExc
        val json = Json.parse("""{ "key" : "value"}""")
				var result: Option[NoSuchElementException] = None

				val future = (marshaller ? UnMarshall(json, classOf[Envelope])).breakOnFailure
        Await.ready(future, timeout.duration)
				future.onComplete {
					case Failure(e: NoSuchElementException) => result = Some(e)
				}

        if(result.isEmpty) fail("should have failed")
        result.get.getClass shouldBe classOf[NoSuchElementException]
			}
		}
	}
}
