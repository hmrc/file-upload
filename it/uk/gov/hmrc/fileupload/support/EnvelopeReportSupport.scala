package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json

object EnvelopeReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty) = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty) = s"""
     |{
     |  "callbackUrl": "${args.getOrElse("callbackUrl", "http://localhost:8900")}",
     |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z")}",
     |  "metadata": {
     |    "anything": "the caller wants to add to the envelope"
     |  },
     |  "constraints": {
     |            "maxItems": 56,
     |            "maxSize": "08MB",
     |            "maxSizePerItem": "08KB"
     |  }
     |}
		 """.stripMargin

}
