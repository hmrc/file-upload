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

import org.scalatest.Suite
import org.scalatestplus.play.OneServerPerSuite
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.mongo.MongoSpecSupport

trait ITApplicationComponents extends OneServerPerSuite with MongoSpecSupport {
  this: Suite =>
  override implicit lazy val app = components.application
  override lazy val port: Int = 9000

  // accessed to get the components in tests
  lazy val components: ApplicationModule = new ITestApplicationModule(context)

  lazy val context: ApplicationLoader.Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new java.io.File("."), classLoader, Mode.Test)
    ApplicationLoader.createContext(env, initialSettings = Map(
      "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
      "auditing.enabled" -> "false",
      "feature.basicAuthEnabled" -> "true"
    ))
  }

}

class ITestApplicationModule(context: Context) extends ApplicationModule(context = context) {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq()
}
