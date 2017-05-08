/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers.constraints

import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.controllers.{CreateEnvelopeRequest, EnvelopeConstraints, EnvelopeConstraintsUserSetting}
import uk.gov.hmrc.fileupload.write.envelope._
import uk.gov.hmrc.fileupload.{EnvelopeId, EventBasedGWTSpec, FileId, FileRefId}

class EnvelopeConstraintsRequestSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope] {

  override val handler = Envelope

  override val defaultStatus: Envelope = Envelope()

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val envelopeCreated = EnvelopeCreated(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)),
    Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(100, 26214400, 10485760, List("application/pdf", "image/jpeg", "application/xml"))))

  val createEnvelopeRequestWithoutMaxNoFilesConstraints: Option[EnvelopeConstraints] = {
    CreateEnvelopeRequest.formatUserEnvelopeConstraints(EnvelopeConstraintsUserSetting(None, Some("26214400"),
      Some("10MB"), Some(List("application/pdf", "image/jpeg", "application/xml"))))
  }

  val createEnvelopeRequestWithoutMaxSizeConstraints: Option[EnvelopeConstraints] = {
    CreateEnvelopeRequest.formatUserEnvelopeConstraints(EnvelopeConstraintsUserSetting(Some(100), None,
      Some("10485760"), Some(List("application/pdf", "image/jpeg", "application/xml"))))
  }

  val createEnvelopeRequestWithoutMaxSizePerItemConstraints: Option[EnvelopeConstraints] = {
    CreateEnvelopeRequest.formatUserEnvelopeConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("25MB"),
      None, Some(List("application/pdf", "image/jpeg", "application/xml"))))
  }

  val createEnvelopeRequestWithoutTypeConstraints: Option[EnvelopeConstraints] = {
    CreateEnvelopeRequest.formatUserEnvelopeConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("26214400"),
      Some("10485760"), None))
  }

  feature("CreateEnvelope with constraints") {

    scenario("Create new envelope with out set max. no. of files constraint") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), createEnvelopeRequestWithoutMaxNoFilesConstraints),
        envelopeCreated
      )
    }

    scenario("Create new envelope with out the constraint for Max size per envelope") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), createEnvelopeRequestWithoutMaxSizeConstraints),
        envelopeCreated
      )
    }

    scenario("Create new envelope with out the constraint for Max size per item") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), createEnvelopeRequestWithoutMaxSizePerItemConstraints),
        envelopeCreated
      )
    }

    scenario("Create new envelope with out the constraint for content type") {

      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), createEnvelopeRequestWithoutTypeConstraints),
        envelopeCreated
      )
    }

    scenario("Create new envelope with number of items exceeding limit") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(122, 1000, 123, List("application/pdf", "image/jpeg", "application/xml")))),
        InvalidMaxItemCountConstraintError
      )
    }

    scenario("Create new envelope with number of items < 1") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(0, 1000, 123, List("application/pdf", "image/jpeg", "application/xml")))),
        InvalidMaxItemCountConstraintError
      )
    }

    scenario("Create new envelope with out of bounds max size per item constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(12, 1000, Integer.MAX_VALUE, List("application/pdf", "image/jpeg", "application/xml")))),
        InvalidMaxSizePerItemConstraintError
      )
    }

    scenario("Create new envelope with out of bounds max size constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(12, Integer.MAX_VALUE, 23434, List("application/pdf", "image/jpeg", "application/xml")))),
        InvalidMaxSizeConstraintError
      )
    }

    scenario("Create new envelope with out valid content type") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some("http://www.callback-url.com"), Some(new DateTime(0)), Some(Json.obj("foo" -> "bar")), Some(EnvelopeConstraints(12, 1000, 23434, List("application/pd")))),
        EnvelopeContentTypesError
      )
    }

  }
}