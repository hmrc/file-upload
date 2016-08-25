package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json

object EnvelopeReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty) = Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty) = s"""
     |{
     |  "constraints": {
     |    "contentTypes": [
     |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
     |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
     |      "application/vnd.oasis.opendocument.spreadsheet"
     |    ],
     |    "maxItems": 100,
     |    "maxSize": "12GB",
     |    "maxSizePerItem": "10MB"
     |  },
     |  "callbackUrl": "${args.getOrElse("callbackUrl", "http://absolute.callback.url")}",
     |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z")}",
     |  "metadata": {
     |    "anything": "the caller wants to add to the envelope"
     |  }
     |}
		 """.stripMargin

  def responseBodyAsJson(id: String, args: Map[String, Any] = Map.empty) = Json.parse(responseBody(id, args))

  def responseBody(id: String, args: Map[String, Any] = Map.empty) = s"""
    |{
    |  "id": "$id",
    |  "constraints": {
    |    "contentTypes": [
    |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    |      "application/vnd.oasis.opendocument.spreadsheet"
    |    ],
    |    "maxItems": 100,
    |    "maxSize": "12GB",
    |    "maxSizePerItem": "10MB"
    |  },
    |  "callbackUrl": "http://absolute.callback.url",
    |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z") }",
    |  "metadata": {
    |    "anything": "the caller wants to add to the envelope"
    |  }
    |}
    """.stripMargin

}
