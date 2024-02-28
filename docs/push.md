## Push

For destinations registered for push (configured with `routing.destinations`), when the envelope is routed, a request is made to SDES (using the registered url `routing.pushUrl`) to route the envelope. The request includes a pre-signed AWS Url from `file-upload-frontend` for SDES to download the envelope as a zip.

In addition to acknowledging the push request with a 200, SDES will make callbacks  (to internal `POST /file-routing/sdes-callback` endpoint) with the following states:

| State | meaning | file-upload action |
| --- | --- | --- |
| `FileReceived` | SDES has successfully downloaded the file. | The envelope will be marked as routed (and Closed), which will stop retries.
| `FileProcessingFailure` | There was a problem processing the envelope downstream. | The file will be marked as routed (and Closed). Since it's already in this state, it just serves to keep a log of the failure reason.
| `FileProcessed` | The file was successfully processed | The envelope will be marked as archived.

The push requests are throttled to not overwhelm downstream services. And all envelopes that have not been acknowledged with `FileReceived` within a determined timespan (configured by `routing.pushRetryBackoff`) will be retried. The retries will eventually stop when the files expire in S3.

⚠ **Internal use only**

In exceptional circumstances, if required, it is possible to resend all Closed enveloped (those which were previously processed) by calling `POST /file-routing/resend-closed` via `file-upload-admin-frontend`. This will however take a while to work through them all due to the throttling.
