package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

object FileMetadataReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty) = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty) = s"""
     |{
     |  "name":"${args.getOrElse("name", "test.jpg")}",
     |  "contentType":"${args.getOrElse("contentType", "application/pdf")}",
     |  "metadata":{
     |    "id":"${args.getOrElse("metadata.id", "1234567890")}",
     |    "origin":{
     |      "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
     |      "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
     |      "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
     |      "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
     |      "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}",
     |      "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "1.2.3.4")}"
     |    },
     |    "sender":{
     |      "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
     |    }
     |  }
     |}
		 """.stripMargin

  def responseBodyAsJson(envelopeId: EnvelopeId, fileId: FileId, args: Map[String, Any] = Map.empty) =
    Json.parse(responseBody(envelopeId, fileId, args))

  def responseBody(envelopeId: EnvelopeId, fileId: FileId, args: Map[String, Any] = Map.empty) = prettify(s"""
    |{
    |  "id":"$fileId",
    |  "status":"QUARANTINED",
    |  "name":"${args.getOrElse("name", "test.jpg")}",
    |  "contentType":"${args.getOrElse("contentType", "application/pdf")}",
    |  "length" : 123,
    |  "created":"1970-01-01T00:00:00Z",
    |  "metadata":{
    |    "id":"${args.getOrElse("metadata.id", "1234567890")}",
    |    "origin":{
    |      "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
    |      "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
    |      "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
    |      "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
    |      "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}",
    |      "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "1.2.3.4")}"
    |    },
    |    "sender":{
    |      "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
    |    }
    |  },
    |  "href" : "/file-upload/envelopes/$envelopeId/files/$fileId/content"
    |}
		 """.stripMargin)

  def responseBodyWithFileInfoAsJson(id: String, args: Map[String, Any] = Map.empty) = Json.parse(responseBodyWithFileInfo(id, args))

  def responseBodyWithFileInfo(id: String, args: Map[String, Any] = Map.empty) = s"""
    |{
    |  "id":"$id",
    |  "name":"${args.getOrElse("name", "test.jpg")}",
    |  "contentType":"${args.getOrElse("contentType", "application/pdf")}",
    |  "length":${args.getOrElse("length", 0)},
    |  "created":"${args.getOrElse("created", "")}",
    |  "metadata":{
    |    "id":"${args.getOrElse("metadata.id", "1234567890")}",
    |    "origin":{
    |      "nino":"${args.getOrElse("metadata.origin.nino", "AB123456Z")}",
    |      "token":"${args.getOrElse("metadata.origin.token", "48729348729348732894")}",
    |      "session":"${args.getOrElse("metadata.origin.session", "cd30f8ec-d866-4ae0-82a0-1bc720f1cb09")}",
    |      "agent":"${args.getOrElse("metadata.origin.agent", "292929292")}",
    |      "trustedHelper":"${args.getOrElse("metadata.origin.trustedHelper", "8984293480239480")}",
    |      "ipAddress":"${args.getOrElse("metadata.origin.ipAddress", "1.2.3.4")}"
    |    },
    |    "sender":{
    |      "service":"${args.getOrElse("metadata.sender.service", "some-service-identifier/v1.2.33")}"
    |    }
    |  }
    |}
		 """.stripMargin

}
