### TEST-ONLY ENDPOINTS (DO NOT USE ON PROD) <a name="testonly"></a>
 These endpoints are not available in production and are used for testing purposes. <i>**PLEASE DO NOT USE THESE WITHOUT PERMISSION**</i>.
 
 
 #### RECREATE COLLECTIONS (DO NOT USE)
 Deletes all collections in quarantine and transient. Then recreates the following collections and its indexes.
 
 Quarantine:
 *   quarantine.chunks
 
 Transient:
 *   envelopes-read-model
 *   envelopes.chunks
 *   events
 
 ```
 POST    /file-upload/test-only/recreate-collections
 ```
 
 | Responses    | Status    | Description |
 | --------|---------|-------|
 | Ok  | 200   | Successfully deleted and recreate collections in both Quarantine and Transient.  |
 
 #### EXAMPLE
 Reques (POST): localhost:8899/file-upload/test-only/recreate-collections
 
 Response: 200

[back to README](../README.md)

