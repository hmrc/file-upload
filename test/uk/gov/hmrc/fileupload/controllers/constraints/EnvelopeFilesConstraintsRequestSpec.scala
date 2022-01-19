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

package uk.gov.hmrc.fileupload.controllers.constraints

import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, EnvelopeConstraintsUserSetting}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration
import uk.gov.hmrc.fileupload.write.envelope._

class EnvelopeFilesConstraintsRequestSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope] with TestApplicationComponents {

  override val handler = new EnvelopeHandler(envelopeConstraintsConfigure)

  override val defaultStatus: Envelope = Envelope()

  val fakeDateTime = new DateTime(0)
  val fakeUrl = "http://www.callback-url.com"
  val fakeData: JsObject = Json.obj("foo" -> "bar")

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val defaultEnvelopeFilesConstraints =
    EnvelopeFilesConstraints(defaultMaxItems, defaultMaxSize, defaultMaxSizePerItem, allowZeroLengthFiles =  None)

  val envelopeCreatedByDefaultStatus = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(defaultConstraints))

  val envelopeCreatedByMaxSizePerFile = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, defaultMaxSizePerItem, allowZeroLengthFiles = Some(true))))

  val envelopeCreatedByMaxSizeEnvelope = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, defaultMaxSizePerItem, allowZeroLengthFiles = Some(true))))

  val createEnvelopeRequestWithoutMaxNoFilesConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(None, Some("25MB"),
      Some("10MB")), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutMaxSizeConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), None,
      Some("10MB")), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutMaxSizePerItemConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("25MB"),
      allowZeroLengthFiles = Some(true)), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutTypeConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("25MB"),
      Some("10MB"), allowZeroLengthFiles = Some(true)), envelopeConstraintsConfigure).right.get)
  }

  Feature("CreateEnvelope with constraints") {

    Scenario("Create new envelope with out set max. no. of files constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxNoFilesConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    Scenario("Create new envelope with out the constraint for Max size per envelope") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxSizeConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    Scenario("Create new envelope with out the constraint for Max size per item") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxSizePerItemConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    Scenario("Create new envelope with out the constraint for content type") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutTypeConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    Scenario("Create new envelope with number of items exceeding limit") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(defaultConstraints.copy(maxItems = 101))),
        InvalidMaxItemCountConstraintError
      )
    }

    Scenario("Create new envelope with number of items < 1") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(defaultConstraints.copy(maxItems = 0))),
        InvalidMaxItemCountConstraintError
      )
    }

    Scenario("Create new envelope not over bounds max size per item constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          envelopeCreatedByMaxSizePerFile.constraints.map(_.maxSize).map(size => defaultConstraints.copy(maxSize = size))),
        envelopeCreatedByMaxSizePerFile
      )
    }

    Scenario("Create new envelope not over bounds max size constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(defaultConstraints.copy(maxSize = acceptedMaxSize))),
        envelopeCreatedByMaxSizeEnvelope
      )
    }

    Scenario("Create new envelope with out valid content type") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(defaultConstraints)),
        envelopeCreatedByDefaultStatus
      )
    }
  }
}
