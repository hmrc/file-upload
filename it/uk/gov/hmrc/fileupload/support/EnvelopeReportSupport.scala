package uk.gov.hmrc.fileupload.support

import play.api.libs.json.{JsValue, Json}

object EnvelopeReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty): JsValue = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty): String = s"""
     |{
     |  "callbackUrl": "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
     |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z")}",
     |  "metadata": {
     |    "anything": "the caller wants to add to the envelope"
     |  }
     |}
		 """.stripMargin



  def requestBodyWithConstraints(args: Map[String, Any] = Map.empty): String = s"""
       |{
       |  "constraints" : {
       |    "contentTypes" : [
       |      "${ args.getOrElse("contentType", "application/xml") }"
       |    ],
       |    "maxSize" : "${ args.getOrElse("maxSize", "12MB") }",
       |    "maxSizePerItem" : "${ args.getOrElse("maxSizePerItem", "10MB") }"
       |  },
       |  "callbackUrl" : "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
       |  "expiryDate" : "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z")}",
       |  "metadata" : {
       |    "anything" : "the caller wants to add to the envelope"
       |  }
       |}
		 """.stripMargin

}
