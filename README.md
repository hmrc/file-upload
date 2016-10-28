# file-upload


[![Build Status](https://travis-ci.org/hmrc/file-upload.svg?branch=master)](https://travis-ci.org/hmrc/file-upload) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload/_latestVersion)

This API provides a mechanism whereby a client microservice can define and manage an envelope which can later be filled with files and then optionally routed to another system. The envelope resources are exposed on the /file-upload/envelopes endpoint. Please <i>**DO NOT USE**</i> Test-Only endpoints because they are not available in production and Internal-Use-Only endpoints <i>**WITHOUT PERMISSION.**</i> 



## Run the application locally

To run the application execute

```
sbt run
```

The endpoints can then be accessed with the base url http://localhost:8898/

Test-only Endpoints 

## Table of Contents

*   [Endpoints](#endpoints)
*   [Callback](#callback)
*   [Test-Only Endpoints](#testonly)
*   [Internal-Use-Only Endpoints](#internal)

## Endpoints <a name="endpoints"></a>

### Envelope

#### Create An Envelope
Creates an envelope and auto generates an Id. The body in the http request must be json. Successful response is provided in the Location Header which will have the link of the newly created envelope.
```
POST   	/file-upload/envelopes
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 201   | Successfully created envelope. |
| Bad Request | 400   |  Envelope not created. |  

#### Example
Request (POST): localhost:8898/file-upload/envelopes

Body:
``` json
{
    "callbackUrl": "string representing absolute url",
    "metadata": { "any": "valid json object" }
}
```

Note: All parameters are optional. A [callbackUrl](#callback) is optional but should be provided in order for the service to provide feedback of the envelope's progress.

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

#### Upload File
Uploads a binary file to Transient Store.
```
PUT     /file-upload/envelopes/{envelope-Id}/files/{file-Id}/{file-Ref-Id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully uploaded file a file.  |
| Not Found | 404   |  File or Envelope not found. Unable to Upload. |

#### Example
Request (PUT): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/file-ref-1

Body: Binary File.

Response: 200

#### Download File
Download a file from an envelope.
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

### EVENTS (DO NOT USE)
These endpoints are used by the application itself and <i>**DO NOT REQUIRE**</i> end-user input. Please <i>**DO NOT USE**</i> these endpoints <i>**WITHOUT PERMISSION**</i>.

#### UPDATE EVENT OF A FILE (DO NOT USE)
Updates an event of a file being uploaded to Quarantine and then Scanned. Note: This is automated.

```
POST    /file-upload/events/{eventType}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully update event.
| Bad Request  | 400   | Invalid Event.
| Locked  | 423   | Cannot update event. Routing request is already received for this envelope.

#### EXAMPLE (FOR FILE IN QUARANTINE)
Request (POST): localhost:8898/file-upload/events/FileQuarantinedStored

Body:
```json
{
	"envelopeId": "0b215e97-11d4-4006-91db-c067e74fc653",
	"fileId": "file-id-1",
	"fileRefId": "file-ref-1",
	"created": 1477490659794,
	"name": "myfile.txt",
	"contentType": "pdf",
	"metadata": {}
}
```

Response: 200

#### EXAMPLE (FOR FILE SCANNED)
Request (POST): localhost:8898/file-upload/events/FileScanned

Body:
```json
{
	"envelopeId": "0b215e97-11d4-4006-91db-c067e74fc653",
	"fileId": "file-id-1",
	"fileRefId": "file-ref-1",
	"hasVirus": false
}
```

Response: 200

Note: "hasVirus" depends on the result from clamAV. If there is a virus, then hasVirus is set to "true" otherwise if not then it would be set to "false".

#### SHOW EVENTS OF AN ENVELOPE (DO NOT USE)
Retrieves a list of all events based on the stream Id. 

```
GET     /file-upload/events/{stream-Id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of events based on stream Id.

#### EXAMPLE
Request (GET): localhost:8899/file-upload/test-only/events/0b215e97-11d4-4006-91db-c067e74fc653

Response (In Body):
```json
[
  {
    "eventId": "bbf89c47-ec22-4b5a-917a-fa19f9f2005d",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 1,
    "created": 1477490656518,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.EnvelopeCreated",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653"
    }
  },
  {
    "eventId": "07ea4682-0c2c-49f8-b01a-6128c18ed30f",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 2,
    "created": 1477490659794,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.FileQuarantined",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653",
      "fileId": "file-id-1",
      "fileRefId": "82c1e62c-ddca-468f-a1c9-ca9aa97aa0a2",
      "created": 1477490659610,
      "name": "zKv4Qv6366462570363333088.tmp",
      "contentType": "application/octet-stream",
      "metadata": {
        "foo": "bar"
      }
    }
  },
  {
    "eventId": "29ab824b-e045-4281-9e40-4fd572a03078",
    "streamId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "version": 3,
    "created": 1477490660074,
    "eventType": "uk.gov.hmrc.fileupload.write.envelope.EnvelopeSealed",
    "eventData": {
      "id": "0b215e97-11d4-4006-91db-c067e74fc653",
      "routingRequestId": "ec97bbd0-7be5-4727-8d92-441818a63dcd",
      "destination": "DMS",
      "application": "foo"
    }
  }
]
```

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

## Callback <a name="callback"></a>

The following is an example request to the callbackUrl. Should comprise of:
* Envelope Id
* File Id
* Status which will show the current status. Possible values are QUARANTINED, CLEANED or ERROR
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

## TEST-ONLY ENDPOINTS <a name="testonly"></a>
These endpoints are not available in production and are used for testing purposes. <i>**PLEASE DO NOT USE THESE WITHOUT PERMISSION**</i>.

#### CLEAN UP TRANSIENT (DO NOT USE)
Removes all envelopes, files and chunks in their respective collections inside Transient Storage (Only). 

Note: This does not remove event data and inprogress files.

```
POST    /file-upload/test-only/cleanup-transient
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully cleared Transient.  |

#### EXAMPLE
Request (POST): localhost:8898/file-upload/test-only/cleanup-transient

Response: 200

#### CLEAR COLLECTIONS (DO NOT USE)
Removes everything in all collection in both quarantine and transient. 

```
POST    /file-upload/test-only/clear-collections
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully cleared all collections.  |

#### EXAMPLE
Request (POST): localhost:8898/file-upload/test-only/clear-collections

Response: 200

## INTERNAL USE ONLY ENDPOINTS <a name="internal"></a>
The following endpoints are for internal use. <i>**PLEASE DO NOT USE THESE ENDPOINTS WITHOUT PERMISSION**</i>.

#### SHOW FILES INPROGRESS (DO NOT USE)
Returns a list of files that are inprogress which have been uploaded to Quarantine but did not make it to Transient. Note: Be aware that files can reappear and disappear for a short moment. If it occurs, that means the file has been successfully transferred across.

```
GET     /file-upload/files/inprogress
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of inprogress files.

#### EXAMPLE
Request (GET): localhost:8898/file-upload/files/inprogress

Response (in Body):
```json
[
  {
    "_id": "82c1e62c-ddca-468f-a1c9-ca9aa97aa0a2",
    "envelopeId": "0b215e97-11d4-4006-91db-c067e74fc653",
    "fileId": "file-id-1",
    "startedAt": 1477490659610
  },
  {
    "_id": "9b96ce3e-df86-4c6c-b737-aef1e4c98741",
    "envelopeId": "eb5ec7d2-c6c4-4cf9-935b-9f1d4061fab5",
    "fileId": "1",
    "startedAt": 1477491112228
  },
  {
    "_id": "bcbbc597-a8a4-4870-bd6c-cfea52ab2ced",
    "envelopeId": "a1752950-32ab-4bdb-a918-0ee9141ac305",
    "fileId": "1",
    "startedAt": 1477491307267
  }
]
```

#### REPLAY EVENTS (DO NOT USE)
Replays events of an envelope.

```
GET     /file-upload/events/{stream-Id}/replay
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully replayed events.  |

#### EXAMPLE
Request (GET): localhost:8898/file-upload/events/0b215ey97-11d4-4006-91db-c067e74fc653/replay

Response: 200

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
