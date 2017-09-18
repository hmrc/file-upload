## FILE UPLOAD PROCESS
Below describes how the “file-upload” process works. The process described below is recommended if the client aims to upload multiple file (clients that aim to only upload a single file and are not interested in routing can follow a different [process](#single-file-process)): -

Some of the endpoints mentioned below live on file-upload-frontend (referred to be low as FE) and the rest live on file-upload (the back end, referred to below as BE). 

### LIFE-CYCLE OF AN ENVELOPE - [see state diagram](../resources/images/envelope-life-cycle.png)

#### 1. Create an envelope
The first step is to create an envelope. The following endpoint is called and the envelope is created (an envelopeId is generated and returned in the responseHeader) : -

```
POST       {BE}/file-upload/envelopes
```
[click here for more details](https://github.com/hmrc/file-upload#create-an-envelope)


An envelope can contain one or more files. Once created, an envelope will be in an OPEN state, and will remain in this state until a “routing request” is sent.

#### 2. Upload file(s)
After the envelope has been created, the client can now send files using the below endpoint: -
```
POST        {FE}/file-upload/upload/envelopes/{envelope-id}/files/{file-id}
```
envelope-id - the ID that was returned in step 1
file-id - a user generated value. This can be any value the use wishes (file-id’s must be unique within an envelope). One request per file.

Files are uploaded to the QUARANTINE bucket and then virus-scanned: -
- if no issues are found, the file is moved to the TRANSIENT bucket (and file status is set to AVAILABLE)
- if the file is deemed to be INFECTED, the file remains in the QUARANTINE bucket (client will be notified if a callbackUrl was provided)

[click here for more details](https://github.com/hmrc/file-upload-frontend#upload-file)


#### 3. Send routing request
 Once all files have been uploaded, the client can send a “routing request”: - 
```
POST       {BE}/file-routing/requests
```
Once this has been sent, the following will happen: -
 - the envelope moves into a SEALED state 
 - and the client can no longer upload files to the given envelope
 - the envelope moves to a CLOSED state

[click here for more details](https://github.com/hmrc/file-upload#create-file-routing-request)


#### 4. Delete envelope
The envelope will remain in a CLOSED state until the envelope is DELETED. The envelope is deleted with the following endpoint
```
DELETE     {BE}/file-transfer/envelopes/{envelope-id}
```
envelope-id - the identifier for the envelope to be deleted (this is a soft delete)

[click here for more details](https://github.com/hmrc/file-upload#soft-delete-an-envelope)


#### Envelope Statuses
See [here](https://github.com/hmrc/file-upload#envelope-statuses) for envelope statuses

### SINGLE FILE PROCESS
Clients that aim to upload a single file and have no need for "routing", can follow the following steps: -
1. [create an envelope](https://github.com/hmrc/file-upload#create-an-envelope)
2. [upload file](https://github.com/hmrc/file-upload-frontend#upload-file)
3. [download file](https://github.com/hmrc/file-upload#download-file)
4. [delete file](https://github.com/hmrc/file-upload#hard-delete-a-file)


### LIFE-CYCLE OF A FILE - [see state diagram](../resources/images/file-life-cycle.png)
- Once uploaded, files go into the QUARANTINE bucket (at this point, the file is in a QUARANTINED state), files remain in this bucket until they have been virus scanned. After virus scanning, if no issues are found, the file moves to a CLEANED state (ready to be moved to the TRANSIENT bucket).

- Once the file is CLEANED, it is moved to the TRANSIENT bucket and the status of the file changes to AVAILABLE.

- If a file is found to have a virus, the file remains in the QUARANTINE bucket and the status changes to INFECTED (click [here](TODO add section about how to recover) for how to recover from this).

#### File Statuses

| Status  | Description  | 
| --------|---------|
| QUARANTINED  |  initial state after upload, file is to be/being virus scanned. File is in the QUARANTINE bucket |
| CLEANED | file has been virus scanned and no issues were found |
| AVAILABLE | file is already is a CLEANED state and has now been moved to the TRANSIENT bucket. File can be downloaded. |
| DELETED | TO BE TESTED |
| INFECTED | file has been virus scanned and an issue was found |

### FAQs
#### Why is my envelope stuck in a SEALED state? And how do I recover from this?
This happens when an envelope contains one or more files that have been found to be infected.

#### What happens to INFECTED files?
If a file is found to be INFECTED, the client has a number of options: - 

##### Get notified
If a callbackUrl was provided, the client will be notified about the infected file, for example: - 
```
  {
    "envelopeId": "0b215ey97-11d4-4006-91db-c067e74fc653",
    "fileId": "file-id-1",
    "status": "ERROR",
    "reason": "VirusDectected"
  }
```
##### Manually check and recover
The client can take the following steps: - 
- Request for the status of the envelope; this will retrieve the current state of the envelope and will list the status of all the files contained within the envelope ([show envelope endpoint](https://github.com/hmrc/file-upload#show-envelope))
- Delete the INFECTED file ([delete file endpoint](https://github.com/hmrc/file-upload#hard-delete-a-file))
- Download the AVAILABLE files ([download file endpoint](https://github.com/hmrc/file-upload#download-file)
- Re-upload files




[back to README](../README.md)