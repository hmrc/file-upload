package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json

object FileMetadataReportSupport {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty) = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty) = s"""
     |{
     |  "name":"${args.getOrElse("name", "test.jpg")}",
     |  "contentType":"${args.getOrElse("contentType", "application/pdf")}",
     |  "revision":${args.getOrElse("revision", 1)},
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

  def responseBodyAsJson(id: String, args: Map[String, Any] = Map.empty) = Json.parse(responseBody(id, args))

  def responseBody(id: String, args: Map[String, Any] = Map.empty) = s"""
    |{
    |  "id":"$id",
    |  "name":"${args.getOrElse("name", "test.jpg")}",
    |  "contentType":"${args.getOrElse("contentType", "application/pdf")}",
    |  "revision":${args.getOrElse("revision", 1)},
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
