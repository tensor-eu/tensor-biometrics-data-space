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
import historyController from "../controllers/historyController";

const router = express.Router()

/**
 * @swagger
 * /api/history/:
 *   get:
 *      summary: Retrieve all the closed cases from the CMS
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [history]
 *      parameters:
 *        - name: page
 *          in: query
 *          required: false
 *          example: 0
 *          description: The zero-indexed number of page to request for paginated results
 *          schema:
 *            type: number
 *            minimum: 0
 *        - name: itemsPerPage
 *          in: query
 *          required: false
 *          example: 10
 *          description: The number of individual cases per page to include for paginated results
 *          schema:
 *            type: number
 *            minimum: 1
 *        - name: filter
 *          in: query
 *          required: false
 *          example: title_like_%test%
 *          description: Only include cases that have variables with certain values. Variable filtering expressions are comma-separated and are structured as follows. A valid parameter value has the form key_operator_value. key is the variable name, operator is the comparison operator to be used and value the variable value. ONLY USE root level variables e.g. title. Valid operator values are:eq - equal to; neq - not equal to; gt - greater than; gteq - greater than or equal to; lt - lower than; lteq - lower than or equal to; like - As in SQL. key and value may not contain underscore or comma characters.
 *          schema:
 *            type: string
 *      responses:
 *        200:
 *          description: An array containing every closed case based on the fitering results
 *          content: 
 *            application/json:
 *              schema:
 *                type: array
 *                items:
 *                  $ref: '#/components/schemas/case'
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'   
 */
router.get('/', historyController.getCases.bind(historyController))

/**
 * @swagger
 * /api/history/{businessKey}:
 *   get:
 *      summary: Retrieve a single closed case based on the unique businessKey
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [history]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case to retrieve
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        200:
 *          description: An array containing every case 
 *          content: 
 *            application/json:
 *              schema:
 *                type: array
 *                items:
 *                  $ref: '#/components/schemas/case'
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
router.get('/:businessKey', historyController.getCase)

/**
 * @swagger
 * /api/history/{businessKey}:
 *   delete:
 *      summary: Delete a single closed case based on the unique businessKey
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [history]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique if of the case to delete
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        204:
 *          description: Deleted!
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.delete('/:businessKey', historyController.deleteCase)

/**
 * @swagger
 * /api/history/{businessKey}/evidence/{evidenceId}:
 *   get:
 *      summary: Retrieve a single evidence file from a closed case
 *      tags: [history]
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case containing the evidence
 *          schema:
 *            type: string
 *            format: uuid-v4
 *        - name: evidenceId
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique id of the evidence file
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        200:
 *          description: The Requested piece of evidence as a file
 *        500:
 *          content: 
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'         
 */
router.get('/:businessKey/evidence/:evidenceId', historyController.getEvidence)

export default router