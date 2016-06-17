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

package uk.gov.hmrc.fileupload

import java.io.File

import com.ning.http.client.AsyncHttpClientConfig
import play.api.libs.ws.WSClient
import play.api.libs.ws.ning._
import play.api.libs.ws._

import scala.language.postfixOps
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
	* Created by jay on 15/06/2016.
	*/
object TestUpload {

	def main(args: Array[String]): Unit ={
		if(args.length < 2){
			println("Usage: TestUpload envelopeId filename")
			System.exit(1)
		}
		implicit val ec = ExecutionContext.global
		val envelopeId = args(0)
		val file = new File(args(1))
		if(!file.exists()){
			println(s"$file does not exist")
			System.exit(555)
		}
		val fileId = file.getName
		val url = s"http://localhost:9000/file-upload/envelope/$envelopeId/file/$fileId/content"

		println(s"uploading $file to envelope($envelopeId) at $url")

		val future = WS.url(url).withHeaders("Content-Type" -> "application/octet-stream").put(file)

			future.onComplete{
			case Success(response) => println(response.body)
			case Failure(t)   =>
				t.printStackTrace()
				println(s"unable to complete upload because of ${t.getMessage}")
		}
		Await.ready(future, 1 minute)
		System.exit(0)
	}

	def WS: WSClient = {
		val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build()
		val builder = new AsyncHttpClientConfig.Builder(config)
		new NingWSClient(builder.build())
	}
}
