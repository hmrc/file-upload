# file-upload


[![Build Status](https://travis-ci.org/hmrc/file-upload.svg?branch=master)](https://travis-ci.org/hmrc/file-upload) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload/_latestVersion)

This API provides a mechanism whereby a client microservice can define and manage an envelope which can later be filled with files and then optionally routed to another system. The envelope resources are exposed on the /file-upload/envelopes endpoint. Please <i>**DO NOT USE**</i> Test-Only endpoints because they are not available in production and the Internal endpoints specified <i>**WITHOUT PERMISSION.**</i> 

## Software Requirements
*   MongoDB 3.2 (3.4 will not work currently)

## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8898/

## Table of Contents

*   [Endpoints](#endpoints)
*   [Callback](#callback)
*   [Intercommunication Endpoints](./docs/intercommunication-endpoints.md)
*   [Test-Only Endpoints](./docs/test-only-endpoints.md)
*   [Internal-Use-Only Endpoints](./docs/internal-endpoints.md)
*   [File Upload Process](./docs/file-upload-process.md)

## Endpoints <a name="endpoints"></a>


### Envelope

#### Create an Envelope
Creates an envelope and auto generates an Id. The body in the http request must be json. Successful response is provided in the Location Header which will have the link of the newly created envelope.
```
POST   	/file-upload/envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 201   | Successfully created envelope. |
| Bad Request | 400   |  Envelope not created, with some reason message |  

#### Example
Request (POST): localhost:8898/file-upload/envelopes

Body:
``` json
{
    "callbackUrl": "string representing absolute url",
    "metadata": { "any": "valid json object" },
    "constraints": 	{
          "maxItems": 5,
          "maxSize": "25MB",
          "maxSizePerItem": "10KB",
          "contentTypes": ["application/pdf","image/jpeg","application/xml"]
        }   
}
```

Note: All parameters are optional. 
A [callbackUrl](#callback) is optional but should be provided in order for the service to provide feedback of the envelope's progress.
All constraints are optional for users, default constraints apply if the value is not specified in the create envelope call.

| Attribute    | Options    | Accepted Values | Default    | Description |
| --------|---------|-------|-------|-------|
| constraints.contentTypes  | optional   | application/pdf<br/>image/jpeg<br/>application/xml<br/>text/xml<br/>application/vnd.ms-excel<br/>application/vnd.openxmlformats-officedocument.spreadsheetml.sheet  | application/pdf<br/>image/jpeg<br/>application/xml<br/>text/xml  | MIME types accepted by this envelope  | 
| constraints.maxItems | optional   |  1-100 |  100  | Number of items allowed in this envelope  | 
| constraints.maxSize | optional   | [1-9][0-9]{0,3}(KB&#124;MB) e.g. 1024KB |  25MB  | Maximum Size (sum of files' sizes) for the envelope (Maximum size 250MB)  | 
| constraints.maxSizePerItem | optional   |  [1-9][0-9]{0,3}(KB&#124;MB) e.g. 1024KB |  10MB  | Maximum Size for each file (Maximum size 100MB)  | 

1. constraints.contentTypes and constraints.maxSizePerItem are applied when the file is uploaded. If validation fails, the user will receive an error.
2. constraints.maxItems and constraints.maxSize are applied when the file is routed. Your application may be able to exceed these limits during upload but will not be able to route the envelope.


Response (in Headers): Location → localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

#### Show Envelope
Shows the envelope and its current details such as status, callbackurl and potentially the current files inside. It should show at least the envelope Id and the status when the envelope was created.
```
GET     /file-upload/envelopes/{envelope-id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully shows envelope details.  |
| Not Found | 404   |  Envelope with id not found. |  


#### Example
Request (GET): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response (in Body):
```json
{
  "id": "0b215e97-11d4-4006-91db-c067e74fc653",
  "callbackUrl": "http://absolute.callback.url",
  "metadata": {
    "anything": "the caller wants to add to the envelope"
  },
  "status": "OPEN"
}
```

#### Envelope Statuses

The following are the possible Envelope Statuses that occur for an Envelope.

| Status  | Description  | 
| --------|---------|
| OPEN  |  Envelope is created and open to upload files. |
| SEALED | A routing request has been made. However, files are not "AVAILABLE" yet. |
| CLOSED | A routing request has been made and files are "AVAILABLE". (The status "SEALED" will automatically changed to "CLOSED" once the file/s has become "AVAILABLE".) |
| DELETED | Envelope has been deleted. |

#### Hard Delete an Envelope
Completely deletes an envelope and any files inside.
```
DELETE  /file-upload/envelopes/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Envelope is deleted successfully.  |
| Bad Request  | 400   | Envelope not deleted. |
| Not Found | 404   |  Envelope not found. |

#### Example 
Request (DELETE): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response: 200

#### Hard Delete a File
Completely deletes a file in an envelope.
```
DELETE  /file-upload/envelopes/{envelope-id}/files/{file-Id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | File is deleted successfully  |
| Bad Request  | 400   | File not deleted. |
| Not Found | 404   |  File not found in envelope. | 

#### Example
Request (DELETE): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1

Response: 200

#### Retrieve Metadata
Returns current metadata information of the file in the envelope. Metadata such as file name are provided by the user.
```
GET     /file-upload/envelopes/{envelope-id}/files/{files-Id}/metadata
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully retrieved metadata.  |
| Not Found | 404   |  Metadata not found. |

#### Example
Request (GET): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/metadata

Response (in Body): 
``` json
{
  "id": "file-id-1",
  "status": "CLEANED",
  "name": "myfile.txt",
  "contentType": "pdf",
  "created": "1970-01-01T00:00:00Z",
  "metadata": {},
  "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/content"
}
```

#### Download File
Download a file from an envelope. To be used as a reaction on callback.
Wherever possible please use routing + dowload the zip endpoints instead of this endpoint.
```
GET   	/file-upload/envelopes/{envelope-id}/files/{file-id}/content
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully download a file.  |
| Not Found | 404   |  File not found. |

#### Example
Request (GET): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/content

Response: Binary file which contains the selected file.


### Routing

#### Create File Routing Request
Changes the status of an envelope to CLOSED and auto generates a routing Id. The status change, rejects any requests to add files to the envelope. This makes it available for file transfer. To make the request, the envelope Id, application and destination must be provided. A response is provided in the Location Header with a link and a new autogenerated routing Id. In regards to MVP, only DMS is supported.
```
POST    /file-routing/requests
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Created  | 201   | Successfully created routing request.  |
| Bad Request  | 400   | Failed to create route request. | 

#### Example
Request (POST): localhost:8898/file-routing/requests

Body:
``` json
{
	"envelopeId":"0b215e97-11d4-4006-91db-c067e74fc653",
	"application":"application/json",
	"destination":"DMS"
}
```

Response(in Headers): Location -> /file-routing/requests/39e0e07d-7969-44ac-9f9c-4f7cc264b027


### Transfer

#### Download List of Envelopes
Returns either a list of all available or selected envelopes (via query string) for routing that have the status CLOSED and information from the routing request provided (above). 
```
GET     /file-transfer/envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of envelopes.

#### Example
Request (GET): localhost:8898/file-transfer/envelopes

Note: Returns a list of all available envelopes for transfer.

#### OR

Request (GET): localhost:8898/file-transfer/envelopes?destination=DMS

Note: Returns a list of all available envelopes going to a specific destination.

Response (in Body):
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
              "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/1/content",
              "name": "original-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 1231222,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/1"
                }
              }
            },
            {
              "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/2/content",
              "name": "another-file-name-on-disk.docx",
              "contentType": "application/vnd.oasis.opendocument.spreadsheet",
              "length": 112221,
              "created": "2016-03-31T12:33:45Z",
              "_links": {
                "self": {
                  "href": "/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/2"
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
| Ok  | 200   | Successfully download zip. |
| Not Found | 404   |  Envelope not found. |


#### Example
Request (GET): localhost:8898/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response: Binary file contains the zipped files.

#### Soft Delete an Envelope 
Changes status of an envelope to DELETED which prevents any service or user from using this envelope.
```
DELETE    /file-transfer/envelopes/{envelope-id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Envelope status changed to deleted.  |
| Bad Request  | 400   |  Invalid request. File |
| Not Found | 404   |  Envelope ID not found. |
| Gone | 410   |  Has Deleted before. |
| Locked | 423   |  Unable to deleted. |

#### Example
Request (DELETE): localhost:8898/file-transfer/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Response: 200


#### Get File Metadata
Gets the metadata for a given file

```
GET        /file-upload/envelopes/{envelope-Id}/files/{file-Id}/metadata
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returnsd file metadata  |
| Not Found | 404   |  Envelope with id {envelopeId} not found. |  
| Not Found | 404   |  File with id: {fileId} not found in envelope: {envelopeId} |   


#### Example
Request (GET): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/0b645e55-22d4-7977-21db-c067e74fc234/metadata

Response (in Body):
```json
{
  "id": "0b215e97-11d4-4006-91db-c067e74fc653",
  "status": "CLEANED",
  "name": "a-filename.pdf",
  "contentType": "application/pdf",
  "length": "1024",
  "created": "1970-01-01T00:00:00Z",
  "revision": "1",
  "metadata": {},
  "href": ""
}
```

## Callback <a name="callback"></a>

The following is an example request to the callbackUrl. Should comprise of:
* Envelope Id
* File Id
* Status which will show the current status. Possible values are QUARANTINED, CLEANED, INFECTED, AVAILABLE or ERROR
* Reason which is optional and only occurs when status is ERROR

Request (POST)
```json
{
  "envelopeId": "0b215ey97-11d4-4006-91db-c067e74fc653",
  "fileId": "file-id-1",
  "status": "ERROR",
  "reason": "VirusDectected"
}
```

Expected response status code: 200



### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
