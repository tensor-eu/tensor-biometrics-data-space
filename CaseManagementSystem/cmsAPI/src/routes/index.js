/*
 * Copyright (c) 2024
 * Thridium
 *
 * This program and the accompanying materials are made
 * available under the terms of the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 * which is available at <<PLACEHOLDER>>
 *
 *  License-Identifier: EUPL-1.2
 *
 *  Contributors:
 *    George Benos (Thridium)
 */

/**
 * @swagger
 *   components:
 *     securitySchemes:
 *       BearerAuth:
 *         type: http
 *         scheme: bearer
 *       InternalWSAuth:
 *         type: apiKey
 *         in: header
 *         name: Authorization
 *     schemas:
 *       newCase:
 *          type: object
 *          required:
 *            - uc_template
 *            - businessKey
 *            - submission
 *            - incident
 *          properties:
 *            businessKey:
 *              type: object
 *              readOnly: true
 *              required:
 *                - value
 *              properties:
 *                value:
 *                  type: integer
 *                  example: b05b953e-a95b-463b-a818-5493473c6d12
 *            title:
 *              type: object
 *              required:
 *                - value
 *              properties:
 *                value:
 *                  type: string
 *                  example: Test case
 *            uc_template:
 *              type: object
 *              required:
 *                - value
 *              properties:
 *                value:
 *                  type: string
 *                  enum: [uc_3]
 *                  example: uc_3
 *            submission:
 *              type: object
 *              description: A collection of fields pertaing to the recording of the case
 *              required:
 *                - value
 *              properties:
 *                value:
 *                  type: object
 *                  properties:
 *                    by:
 *                      type: string
 *                      description: The name of the officer creating the new case
 *                      example: John Smith
 *                    role:
 *                      type: string
 *                      description: The role/rank of the officer creating the new case
 *                      example: officer
 *                    country:
 *                      type: string
 *                      description: The country of the LEA creating the case
 *                      example: Greece
 *                    agency:
 *                      type: string
 *                      description: The name of the LEA creating the new case
 *                      example: Hellenic Police
 *                    ccn:
 *                      type: string
 *                      description: The criminal case number corresponding to the new case in the local case registry
 *                      example: Officer Name
 *                    date:
 *                      type: string
 *                      format: date-time
 *                      description: The date and time this case was created, in RFC 3339 format
 *                      example: 2024-07-10T11:15+03:00
 *            incident:
 *              type: object
 *              description: A collection of fields pertaining to the physical incident that led to the creation of the case
 *              required:
 *                - value
 *              properties:
 *                value:
 *                  type: object
 *                  properties:
 *                    type:
 *                      type: string
 *                      description: The type of the recorded incident
 *                      example: robbery
 *                    date:
 *                      type: string
 *                      format: date-time
 *                      description: The date and time this incident was recorded, in RFC 3339 format
 *                      example: 2024-07-10T11:15+03:00
 *                    address:
 *                      type: string
 *                      example: Wellington Avenue 22
 *                      description: The exact location where the incident took place
 *                    city:
 *                      type: string
 *                      example: Athens
 *                      description: The city/area where the incident took place
 *                    postal_code:
 *                      type: string
 *                      example: 44 555
 *                      description: The postal code of the area where the incident took place
 *                    country:
 *                      type: string
 *                      example: Greece
 *                      description: The country where the incident took place
 *                    description:
 *                      description: A textual representation of the incident
 *                      type: string
 *       case:
 *         type: object
 *         properties:
 *           task:
 *             type: object
 *             properties:
 *               id:
 *                 type: string
 *                 format: uuid
 *                 description: The internal ID of the task (current step) object (USE businessKey for case retrieval)
 *                 example: 1dad972d-226f-4247-ae79-81638a7e4eb9
 *               name:
 *                 type: string
 *                 description: The name of the current step
 *                 example: 'Step3: Request Face Analysis'
 *               created:
 *                 type: string
 *                 format: date-time
 *                 description: The date and time this specific step was triggered
 *                 example: '2024-08-21T12:08:13.830Z'
 *               processInstanceId:
 *                 type: string
 *                 format: uuid
 *                 description: The internal ID of the process (whole case) object associated with the task
 *                 example: 480299c6-74bd-4061-90aa-44d875303298
 *           caseVars:
 *             $ref: '#/components/schemas/newCase'
 *       evidence:
 *         type: object
 *         description: A collection of fields pertaining to the extra evidence (photos, videos, audio) associated with the case
 *         required:
 *           - value
 *         properties:
 *           value:
 *             type: object
 *             properties:
 *               id:
 *                 type: string
 *                 format: uuid-v4
 *                 readOnly: true
 *                 description: The unique id of the piece of evidence
 *                 example: 12345678-1234-1234-1234-1234567890ab
 *               date:
 *                 type: string
 *                 format: date-time
 *                 readOnly: true
 *                 description: The date and time this piece of evidence was submitted, in RFC 3339 format
 *                 example: 2024-07-10T11:15+03:00
 *               url:
 *                 type: string
 *                 format: url
 *                 readOnly: true
 *                 example: http://test-server.com/path/to/file
 *                 description: The url of the physical evidence file
 *               description:
 *                 description: A textual representation of the evidence
 *                 type: string
 *       uc3MatchObject:
 *         type: object
 *         properties:
 *           from:
 *              type: string
 *              description: The connector ID of the data requestor LEA
 *              example: LEA_ID
 *           faceId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded face biometric to be matched against the indexer catalogue
 *           voiceId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded voice biometric to be matched against the indexer catalogue
 *           fingerprintId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded fingerprint biometric to be matched against the indexer catalogue
 *           faceScore:
 *              type: number
 *              description: The cosine similarity score of the matched face biometric to the original query, in the space of [0, 1]
 *              example: 0.5673
 *              minimum:  0
 *              maximum: 1
 *              readOnly: true
 *           voiceScore:
 *              type: number
 *              description: The cosine similarity score of the matched voice biometric to the original query, in the space of [0, 1]
 *              example: 0.9985
 *              minimum:  0
 *              maximum: 1
 *              readOnly: true
 *           fingerprintScore:
 *              type: number
 *              description: The cosine similarity score of the matched fingerprint biometric to the original query, in the space of [0, 1]
 *              example: 0.79556
 *              minimum:  0
 *              maximum: 1
 *              readOnly: true
 *       uc3RequestObject:
 *         type: object
 *         properties:
 *           from:
 *              type: string
 *              description: The connector ID of the data requestor LEA
 *              example: LEA_ID
 *           to:
 *              type: string
 *              description: The connector ID of the data owner LEA
 *              example: LEA_ID
 *           suspectProfileId:
 *              type: string
 *              description: The supesct profile ID the request pertains to, returned from the /match endpoint
 *              example: 1098907
 *           faceId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded face biometric to be submitted to the data owner for verification
 *           voiceId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded voice biometric to be submitted to the data owner for verification
 *           fingerprintId:
 *              type: string
 *              format: uuid
 *              description: The id of the uploaded fingerprint biometric to be submitted to the data owner for verification
 *       uc3DataExchangeObject:
 *         type: object
 *         properties:
 *           suspectProfileId:
 *             type: string
 *             description: The id of the suspect profile to be retrieved
 *             example: 1098907
 *           to:
 *             type: string
 *             enum: [PROVIDER_CONNECTOR_ID, CONSUMER_CONNECTOR_ID]
 *             description: The data onwer's connector id
 *             example: PROVIDER_CONNECTOR_ID
 *       silhouetteAnalysisResponse:
 *         type: object
 *         properties:
 *           evidence_id:
 *             type: string
 *             description: The CMS id of the requested video
 *           person_id:
 *             type: string
 *             description: The gait module id of the requested person
 *           silhouette:
 *             description: The silhouette data provided by the gait module for the specific person
 *             properties:
 *               data:
 *               shape:
 *                 type: array
 *                 items:
 *                   type: number
 *               dtype:
 *                 type: string
 *       error400:
 *         type: object
 *         required:
 *           - status
 *           - message
 *         properties:
 *           status:
 *             type: integer
 *             example: 400
 *           message:
 *             type: string
 *             example: "Request not formatted properly"
 *       error401:
 *         type: object
 *         required:
 *           - status
 *           - message
 *         properties:
 *           status:
 *             type: integer
 *             example: 401
 *           message:
 *             type: string
 *             example: "Unauthorized to access this endpoint. Try to login again"
 *       error403:
 *         type: object
 *         required:
 *           - status
 *           - message
 *         properties:
 *           status:
 *             type: integer
 *             example: 403
 *           message:
 *             type: string
 *             example: "Forbidden: Your account type cannot access this endpoint"
 *       error404:
 *         type: object
 *         required:
 *           - status
 *           - message
 *         properties:
 *           status:
 *             type: integer
 *             example: 404
 *           message:
 *             type: string
 *             example: "Resource does not exist"
 *       error500:
 *         type: object
 *         required:
 *           - status
 *           - message
 *         properties:
 *           status:
 *             type: integer
 *             example: 500
 *           message:
 *             type: string
 *             example: 'Internal server error'
 */

/**
 * @swagger
 *   tags:
 *    - name: cases
 *      description: 'API that handles the generic CRUD operations of a case in the CMS'
 *    - name: use case 3
 *      description: 'API that handles the UC3 specific operations of a case in the CMS'
 *    - name: history
 *      description: 'API that handles closed (deleted, finalized) cases in the CMS'
 */
