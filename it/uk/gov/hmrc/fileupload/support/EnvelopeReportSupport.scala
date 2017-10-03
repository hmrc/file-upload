package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json

object EnvelopeReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty) = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty) = s"""
     |{
     |  "callbackUrl": "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
     |  "metadata": {
     |    "anything": "the caller wants to add to the envelope"
     |  }
     |}
		 """.stripMargin

  def requestBodyWithConstraints(args: Map[String, Any] = Map.empty) = s"""
       |{
       |  "constraints" : {
       |    "maxSize" : "${ args.getOrElse("maxSize", "12MB") }",
       |    "maxSizePerItem" : "${ args.getOrElse("maxSizePerItem", "10MB") }"
       |  },
       |  "callbackUrl" : "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
       |  "metadata" : {
       |    "anything" : "the caller wants to add to the envelope"
       |  }
       |}
		 """.stripMargin

  def requestBodyWithLowConstraints(args: Map[String, Any] = Map.empty) = s"""
      |{
      |  "constraints" : {
      |    "maxItems" : ${ args.getOrElse("maxItems", 1) },
      |    "maxSize" : "1MB",
      |    "maxSizePerItem" : "1MB"
      |  },
      |  "callbackUrl" : "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
      |  "metadata" : {
      |    "anything" : "the caller wants to add to the envelope"
      |  }
      |}
		 """.stripMargin

}
