# file-upload

[![Build Status](https://travis-ci.org/hmrc/file-upload.svg?branch=master)](https://travis-ci.org/hmrc/file-upload) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload/_latestVersion)

This API provides a mechanism whereby a client microservice can define and manage an envelope which can later be filled with files and then optionally routed to another system.
The envelope resources are exposed on the /file-upload/envelope endpoint.

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8898/file-upload/

## Endpoints

Envelope
POST    /file-upload/envelopes

GET     /file-upload/envelopes/:envelope-id

DELETE  /file-upload/envelopes/:envelope-id

File
POST    /file-upload/envelopes/:envelope-id/files/:file-id/metadata

GET   	/file-upload/envelopes/:envelope-id/files/:file-id/metadata

GET   	/file-upload/envelopes/:envelope-id/files/:file-id/content

Client Routing
POST    /file-routing/requests

File Transfer
GET     /file-transfer/envelopes

GET     /file-transfer/envelopes/:envelope-id

DELETE    /file-transfer/envelopes/:envelope-id




[RAML definition](raml/file-upload.raml)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
