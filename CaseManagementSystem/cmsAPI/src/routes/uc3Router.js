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

//@ts-nocheck
import express from 'express';
import uc3Controller from "../controllers/uc3Controller";

const router = express.Router()

/**
 * @swagger
 * /api/cases/{businessKey}/match:
 *   post:
 *      summary: Request a biometrics match, as part of UC3 flow
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [use case 3]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the relevant case
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *                $ref: '#/components/schemas/uc3MatchObject'   
 *      responses:
 *        200:
 *          description: An array of matching scores for each biometric modality found in each connector that participates in the Data Space
 *          content: 
 *            application/json:
 *              schema:
 *                type: array
 *                items:
 *                  $ref: '#/components/schemas/uc3MatchObject'
 *        404:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error404'      
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.post('/:businessKey/match', uc3Controller.match.bind(uc3Controller))

/**
 * @swagger
 * /api/cases/{businessKey}/dsp-request:
 *   post:
 *      summary: Request the creation of a dataRequest, as part of UC3 flow
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [use case 3]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the relevant case
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *              $ref: '#/components/schemas/uc3RequestObject'
 *      responses:
 *        200:
 *          description: The hex data of the dataRequest, ready to be signed by the end user
 *          content: 
 *            application/json:
 *              schema:
 *                type: string
 *                example: "8998436035900000000000003288372852852528"
 *        404:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error404'      
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.post('/:businessKey/dsp-request', uc3Controller.createRequest.bind(uc3Controller))

// /**
//  * @swagger
//  * /api/cases/{businessKey}/create-dsp-response:
//  *   post:
//  *      summary: Request the creation of a dataResponse, as part of UC3 flow
//  *   security:
//  *        - BearerAuth: []
//  *        - InternalWSAuth: []
//  *      tags: [use case 3]
//  *      parameters:
//  *        - name: businessKey
//  *          in: path
//  *          required: true
//  *          example: 12345678-1234-1234-1234-1234567890ab
//  *          description: The unique businessKey of the relevant case
//  *          schema:
//  *            type: string
//  *            format: uuid-v4
//  *      requestBody:
//  *        required: true
//  *        content:
//  *          application/json:
//  *            schema:
//  *              $ref: '#/components/schemas/uc3RequestObject'   
//  *      responses:
//  *        200:
//  *          description: The hex data of the dataResponse, ready to be signed by the end user
//  *          content: 
//  *            application/json:
//  *              schema:
//  *                type: string
//  *                example: "8998436035900000000000003288372852852528"
//  *        404:
//  *          content: 
//  *            application/json:
//  *              schema:
//  *                $ref: '#/components/schemas/error404'      
//  *        500:
//  *          content: 
//  *            application/json:
//  *              schema:
//  *                $ref: '#/components/schemas/error500'         
//  */
//Handled outside the CMS, directly by the connector
//router.post('/:businessKey/create-dsp-response', uc3Controller.createResponse.bind(uc3Controller))

/**
 * @swagger
 * /api/cases/receive-dsp-response:
 *   post:
 *      summary: Receive a signed & deployed response from the data onwer's connector, as part of UC3 flow
 *      tags: [use case 3]
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *              type: object
 *              properties:
 *                data:
 *                  type: string
 *                  example: "0x8998436035900000000000003288372852852528"
 *                  description: The hex data of the received dataResponse transaction that was signed by the owner
 *                from:
 *                  type: string
 *                  example: "0x8998436035900000000000003288372852852528"
 *                  description: The ethereum address of the data owner who signed the transaction
 *                to:
 *                  type: string
 *                  example: "0x8998436035900000000000003288372852852528"
 *                  description: The ethereum address of the data requestor who is granted access (or not) via the transaction
 *      responses:
 *        200:
 *          description: The hex data of the dataResponse, ready to be signed by the end user
 *          content: 
 *            application/json:
 *              schema:
 *                type: string
 *                example: "8998436035900000000000003288372852852528"
 *        404:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error404'      
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.post('/receive-dsp-response', uc3Controller.receiveResponse.bind(uc3Controller))

/**
 * @swagger
 * /api/cases/{businessKey}/data-exchange:
 *   post:
 *      summary: Proceed with the actual data exchange, as part of UC3
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [use case 3]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the relevant case
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *              $ref: '#/components/schemas/uc3DataExchangeObject'
 *      responses:
 *        200:
 *          description: The retrieved file from the data owner's dataspace connector
 *          content: 
 *            application/json:
 *              schema:
 *                type: string
 *                format: file
 *        404:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error404'      
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.post('/:businessKey/data-exchange', uc3Controller.dataExchange.bind(uc3Controller))

/**
 * @swagger
 * /api/cases/{businessKey}/complete-analysis:
 *   put:
 *      summary: Mark the completion of the internal analysis period, as part of UC3
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [use case 3]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the relevant case
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        205:
 *          description: A simple acknowledgement of the user's completion of the internal analysis step
 *        404:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error404'      
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.put('/:businessKey/complete-analysis', uc3Controller.completeAnalysis.bind(uc3Controller))

export default router
