# file-upload


[![Build Status](https://travis-ci.org/hmrc/file-upload.svg?branch=master)](https://travis-ci.org/hmrc/file-upload) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload/_latestVersion)

This API provides a mechanism whereby a client microservice can define and manage an envelope which can later be filled with files and then optionally routed to another system.
The envelope resources are exposed on the /file-upload/envelopes endpoint.

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8898/file-upload/

## Endpoints

Alternatively  look  here for a [RAML definition](raml/file-upload.raml)

### Envelope

#### Create An Envelope
Creates an envelope and auto generates an Id. Successful response provides in the headers the link  and the envelope Id.
```
POST   	/file-upload/envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 201   | Envelope Successfully Created |
| Bad Request | 400   |  Envelope Not Created |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |    

#### Example
| POST Request   | Body   | Response (in Headers) |
| --------|---------|-------|
| localhost:8898/file-upload/envelopes  | {}   | Location → localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653 |

#### Show Envelope
Show Envelope which comprises of Envelope Id and the current status.
```
GET     /file-upload/envelopes/{envelope-id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File Successfully uploaded  |
| Not Found | 404   |  Envelope ID not found. |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |  


#### Example

Request (GET): localhost:8898/file-upload/envelopes/46d95a7c-c03b-484c-ac70-bf760cd36510 

Response (Body):
```json
{
    "id": "46d95a7c-c03b-484c-ac70-bf760cd36510", 
    "status": "OPEN"
} 
```

#### Hard Delete an Envelope
Completely deletes an envelope and its contents from the front end database.
```
DELETE  /file-upload/envelopes/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Accepted  | 202   | Deleted  |
| Bad Request  | 400   | Envelope not deleted |
| Not Found | 404   |  Envelope not found. |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |  

#### Example 

Request (Delete): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

#### Download File
Downloads a file from the envelope
```
GET   	/file-upload/envelopes/{envelope-id}/files/{file-id}/content
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Download a file.  |
| Not Found | 404   |  File not found. |
| Internal Server Error | 500   |  INTERNAL_SERVER_ERROR |

Request (Get): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/content

Response: Download File

### Routing

#### Create File Routing Request
Changes the status of an Envelope to CLOSED and rejects any requests to add files to the envelope. This makes it available for file transfer.
```
POST    /file-routing/requests
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Created  | 201   | Successfully created event CLOSED |
| Bad Request  | 400   |  Invalid Request. |
| Bad Request  | 400   |  Destination not supported; Routing request already received for envelope |
| Bad Request | 400   |  File contain errors; Envelope not found |
| Forbidden | 403   |  Not Authorised. |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |  

#### Example
Request (POST): localhost:8898/file-routing/requests

### Transfer

#### Download List of Envelopes
Retrieves a list of envelopes for routing. This applies to all envelopes that are closed.
```
GET     /file-transfer/envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successful. Returns a list of Envelopes.
| Bad Request  | 400   |  Invalid Request. |  
| Forbidden | 403   |  Not Authorised. |  
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |  
| Service Unavailable  | 503   |  INTERNAL_SERVER_ERROR|  

#### Example

Request (GET): localhost:8898/file-transfer/envelopes

Response:
```json
{
  "_links": {
    "self": {
      "href": "http://full.url.com/file-transfer/envelopes?destination=DMS"
    }
  },
  "_embedded": {
    "envelopes": [
      {
        "id": "0b215e97-11d4-4006-91db-c067e74fc653",
        "destination": "DMS",
        "application": "application:digital.forms.service/v1.233",
        "_embedded": {
          "files": [
            {
              "href": "/file-upload/envelopes/b215e97-11d4-4006-91db-c067e74fc653/files/1/content",
              "name": "original-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 1231222,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/b215e97-11d4-4006-91db-c067e74fc653/files/1"
                }
              }
            },
            {
              "href": "/file-upload/envelopes/b215e97-11d4-4006-91db-c067e74fc653/files/2/content",
              "name": "another-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 112221,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/b215e97-11d4-4006-91db-c067e74fc653/files/2"
                }
              }
            }
          ]
        },
        "_links": {
          "self": {
            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653"
          },
          "package": {
            "href": "/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653",
            "type": "application/zip"
          },
          "files": [
            {
              "href": "/files/2"
            }
          ]
        }
      }
    ]
  }
}
```


#### Download Zip
Downloads a zip file which is the envelope and its contents.
```
GET     /file-transfer/envelopes/{envelope-id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File Successfully uploaded.  |
| Partial Content  | 206   |   Partially Downloaded. |
| Bad Request  | 400   |  Invalid Request. File not uploaded. |
| Forbidden | 403   |  Not Authorised. |
| Not Found | 404   |  Envelope ID not found. |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |  


#### Example

Request (GET): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response: Download Zip file

#### Soft Delete an Envelope 
Changes status of an envelope to DELETED which prevents any service or user from using this envelope.
```
DELETE    /file-transfer/envelopes/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Deleted  |
| Bad Request  | 400   |  Invalid Request. File not uploaded. |
| Not Found | 404   |  Envelope ID not found. |
| GONE | 410   |  Has Deleted before |
| Locked | 423   |  Unable to Deleted |
| Internal Server Error  | 500   |  INTERNAL_SERVER_ERROR |

#### Example
Request (DELETE): localhost:8898/file-transfer/envelopes

Response: Soft Envelope Deleted

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
