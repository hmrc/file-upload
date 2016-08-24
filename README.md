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
            
[RAML definition](raml/file-upload.raml)

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
