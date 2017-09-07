## INTERNAL USE ONLY ENDPOINTS (DO NOT USE AT ALL) <a name="internal"></a>
The following endpoints are for internal use. <i>**PLEASE DO NOT USE THESE ENDPOINTS WITHOUT PERMISSION**</i>.

#### SHOW ENVELOPES BY STATUS (DO NOT USE)
Returns a list of envelopes by their status.

```
GET     /file-upload/envelopes
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully return list of envelopes

#### EXAMPLE
Request (GET): localhost:8898/file-upload/envelopes

Response: 200

#### CREATE ENVELOPE WITH ID (DO NOT USE)

```
PUT   	/file-upload/envelopes/{envelopeId}
```
| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 201   | Successfully created envelope. |
| Bad Request | 400   |  Envelope not created. |  

#### EXAMPLE
Request (PUT): localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

Body:
``` json
{
    "callbackUrl": "string representing absolute url",
    "metadata": { "any": "valid json object" }
}
```

Response (in Headers): Location → localhost:8898/file-upload/envelopes/0b215e97-11d4-4006-91db-c067e74fc653

#### MANUALLY SEND COMMANDS (DO NOT USE)

```
POST    /file-upload/commands/{commandType}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Command successfully processed.
| Bad Request | 400   | Incorrect Command.
| Locked | 423   | Envelope already routed and received.


#### EXAMPLE
Request (POST): localhost:8898/file-upload/unsealenvelope

In Body:
```json
{
    "id": "0b215e97-11d4-4006-91db-c067e74fc653"
}
```

Response: 200

#### SHOW EVENTS OF AN ENVELOPE (DO NOT USE)
Retrieves a list of all events based on the stream Id. 

```
GET     /file-upload/events/{stream-Id}
```

| Responses    | Status    | Description |
| --------|---------|-------|
| Ok  | 200   | Successfully returns a list of events based on stream Id.

#### EXAMPLE
Request (GET): localhost:8898/file-upload/events/0b215e97-11d4-4006-91db-c067e74fc653

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

#### MANUALLY DELETE IN-PROGRESS FILE
Removes inprogress data by their file reference Id.

```
DELETE     /file-upload/files/inprogress/:fileRefId 
```

| Responses    | Status    | Description |
| Ok  | 200   | Successfully deleted inprogress file data.  |

#### EXAMPLE
Request (DELETE): localhost:8898/file-upload/files/inprogress/a1752950-32ab-4bdb-a918-0ee9141ac305

Response: 200 

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

[back to README](../README.md)

