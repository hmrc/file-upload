## INTERCOMMUNICATION ENDPOINTS (DO NOT USE) <a name="auto"></a>
The following endpoint is used by the application itself and <i>**DOES NOT REQUIRE**</i> user input. <i>**PLEASE DO NOT USE WITHOUT PERMISSION**</i>

#### UPDATE EVENT OF A FILE (DO NOT USE)
Updates an event of a file being uploaded to Quarantine and then Scanned.

```
POST    /file-upload/events/{eventType}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully update event.
| Bad Request  | 400   | Invalid Event.
| Locked  | 423   | Cannot update event. Routing request is already received for this envelope.

#### EXAMPLE (FOR FILE IN QUARANTINE)
Request (POST): localhost:8898/file-upload/events/FileQuarantineStored

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

#### UPLOAD FILE (DO NOT USE)
Uploads a binary file to Transient Store. This endpoint cannot be used directly to upload a file and any attempts to do so will be rejected. Only files that have been uploaded to the frontend, that have been quarantined and then scanned are accepted.
```
PUT     /file-upload/envelopes/{envelope-Id}/files/{file-Id}/{file-Ref-Id}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully uploaded file a file.  |
| Not Found | 404   |  File or Envelope not found. Unable to Upload. |

#### EXAMPLE
Request (PUT): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653/files/file-id-1/file-ref-1

Body: Binary File.

Response: 200

[back to README](../README.md)
