@FILE-UPLOAD
@SPRINT-5

# User Stories FILE-63, FILE-64, FILE-65
# Basic tests which cover the initial scenarios before any files have been attached

Feature: Create, Retrieve and Delete Envelope

  As a client microservice
  I want to limit what my users can upload to my service
  So that I can protect the MDTP from flooding attacks.


  Scenario: Create a new Envelope with empty body
    Given I have an empty JSON request
    When I invoke POST /file/upload/envelope
    Then I will receive a 201 CREATED response
    And a new Envelope record with no attributes will be created
    And the Envelope ID will be returned in the location header


  Scenario: Create a new Envelope using basic sample
    Given I have the following JSON:
      """
      [{"constraints": {
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
      "expiryDate": 1467893730000,
      "metadata": {
      "anything": "the caller wants to add to the envelope"}
      }]
      """
    When I invoke POST /file/upload/envelope
    Then I will receive a 201 CREATED response
    And a new Envelope record will be created
    And the Envelope ID will be returned in the location header



  Scenario: Retrieve Envelope using basic sample
    Given the file-upload service contains an existing Envelope with <envelope-id>
    When I invoke GET /file-upload/envelope/{<envelope-id>}
    Then I will receive a 200 OK response
    And the response body will  JSON:
      """
      [{"_id": "<envelope-id>",
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
      "expiryDate": 1467893730000,
      "metadata": {
      "anything": "the caller wants to add to the envelope"}
      }]
      """


  Scenario: Attempt to create an Envelope providing an Envelope ID
    Given the file-upload service contains an existing envelope with <envelope-id>
    And I have the following JSON
      """
      [{"_id": "<envelope-id>",
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
      "expiryDate": 1467893730000,
      "metadata": {
      "anything": "the caller wants to add to the envelope"}
      }]
      """
    When I invoke POST /file/upload/envelope
    Then I will receive a 400 Failure response




