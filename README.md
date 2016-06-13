# file-upload

[![Build Status](https://travis-ci.org/hmrc/file-upload.svg?branch=master)](https://travis-ci.org/hmrc/file-upload) [ ![Download](https://api.bintray.com/packages/hmrc/releases/file-upload/images/download.svg) ](https://bintray.com/hmrc/releases/file-upload/_latestVersion)

This API provides a mechanism whereby a client microservice can define and manage an envelope which can later be filled with files and then optionally routed to another system.
The envelope resources are exposed on the /file-upload/envelope endpoint.

##Create Envelope
###Request
```
Authorization : Bearer {AUTH_BEARER}
POST yourdomain.com/file-upload/envelope

{PAYLOAD}
```

###Request Parameters
<md-table-container>
<table>
   <thead>
      <tr>
         <th>
            <div>Field</div>
         </th>
         <th>
            <div>Values</div>
         </th>
         <th>
            <div>Description</div>
         </th>
      </tr>
   </thead>
   <tbody>
      <tr>
         <td>id</td>
         <td>&nbsp;</td>
         <td>Unique Identifier for this envelope.</td>
      </tr>
      <tr>
         <td>constraints.contentTypes</td>
         <td>MIME</td>
         <td>MIME types accepted by this envelope</td>
      </tr>
      <tr>
         <td>constraints.maxItems</td>
         <td>1-999</td>
         <td>Number of items allowed in this envelope. Default is <strong>1</strong></td>
      </tr>
      <tr>
         <td>constraints.maxSize</td>
         <td>0-999 KB|MB|GB|TB|PB</td>
         <td>Maximum Size for the envelope. If not set, unlimited size</td>
      </tr>
      <tr>
         <td>constraints.maxSizePerItem</td>
         <td>0-999 KB|MB|GB|TB|PB</td>
         <td>Maximum Size for each file. If not set, unlimited size</td>
      </tr>
      <tr>
         <td>callbackUrl</td>
         <td>URL</td>
         <td>
            <p>Callback URL for status updates. A status message is sent to this endpoint for all updates to files within an envelope:</p>
            <pre>{<br>    "file" : "/file-upload/envelope/:envelope-id/:file-id",<br>    "status" : "QUARANTINED|CLEANED|AVAILABLE|TRANSFER-PENDING|IN-TRANSFER|TRANSFERRED"<br>}&nbsp;</pre>
         </td>
      </tr>
      <tr>
         <td>expiryDate</td>
         <td>Date</td>
         <td>If present, the envelope will automatically expire (and be removed) at the date and time shown.</td>
      </tr>
      <tr>
         <td>metadata</td>
         <td>
            <p>Json Object</p>
         </td>
         <td>Any metadata the client wishes to store against the envelope. Will be returned, unmodified, in a GET call</td>
      </tr>
   </tbody>
</table>
</md-table-container>

###Example payload:
```json
{
  "constraints": {
    "contentTypes": [
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.oasis.opendocument.spreadsheet"
    ],
    "maxItems": 100,
    "maxSize": "12GB",
    "maxSizePerItem": "10MB"
  },
  "callbackUrl": "http://absolute.callback.url",
  "expiryDate": "2016-06-13T15:15:30Z",
  "metadata": {
    "anything": "the caller wants to add to the envelope"
  }
}
```

###Response
If the request is successfully processed the service responds with a 201 status and a Location header containing the url pointing to the newly created resource.

In case of failure the service responds with a json containing the reason of failure:
```json
{
   "error": {
      "msg": "Service unavailable"
   }
}
```

##Read Envelope
###Request
```
Authorization : Bearer {AUTH_BEARER}
GET yourdomain.com/file-upload/envelope/{ENVELOPE_ID}
```

###Response
If successfully processed the response contains the envelope details specified at creation time plus the id and the list of files currently stored:
```json
{
  "id": "0b215e97-11d4-4006-91db-c067e74fc653",
  "constraints" : {
    "contentTypes" : [
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.oasis.opendocument.spreadsheet"
    ],
    "maxItems" : 100,
    "maxSize" : "12GB",
    "maxSizePerItem" : "10MB",
  },
  "expiryDate": "2016-04-07T13:15:30Z",
  "metadata" : {
      "anything": "the caller wants to add to the envelope"
  },
  "files" : [
     {
        "rel" : "file",
        "href" : "/file-upload/envelope/12345-34567-22222/file-name%20is-encoded.xlsx"
     },
     {
        "rel" : "file",
        "href" : "/file-upload/envelope/12345-34567-22222/another-file-name.docx"
     }
  ]
}
```

In case of failure the service responds with a json containing the reason of failure:
```json
{
   "error": {
      "msg": "Service unavailable"
   }
}
```

##Delete Envelope
###Request
```
Authorization : Bearer {AUTH_BEARER}
DELETE yourdomain.com/file-upload/envelope/{ENVELOPE_ID}
```

###Response
If successfull the service responds with status 200 OK and no body.

In case of failure the service responds with a json containing the reason of failure:
```json
{
   "error": {
      "msg": "Service unavailable"
   }
}
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
