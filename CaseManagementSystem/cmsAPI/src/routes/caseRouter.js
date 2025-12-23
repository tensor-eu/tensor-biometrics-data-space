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
import express from "express";
import caseController from "../controllers/caseController";
import upload from "../middleware/fileUpload";

const router = express.Router();

/**
 * @swagger
 * /api/cases/:
 *   post:
 *      summary: Create a new criminal case in the case management system
 *      tags: [cases]
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *              $ref: '#/components/schemas/newCase'
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      responses:
 *        201:
 *          description: An object containing the businessKey (external ID) of the new case
 *          content:
 *            application/json:
 *              schema:
 *                type: object
 *                properties:
 *                  businessKey:
 *                    type: string
 *                    format: uuid-v4
 *                    example: 12345678-1234-1234-1234-1234567890ab
 *                    description: Used to perform search operations on the case
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.post("/", caseController.createCase.bind(caseController));

/**
 * @swagger
 * /api/cases/:
 *   get:
 *      summary: Retrieve all the cases from the CMS
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
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
 *          description: An array containing every case
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
router.get("/", caseController.getCases.bind(caseController));

/**
 * @swagger
 * /api/cases/{businessKey}:
 *   get:
 *      summary: Retrieve a single case based on the unique businessKey
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
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
router.get("/:businessKey", caseController.getCase.bind(caseController));

/**
 * @swagger
 * /api/cases/{businessKey}:
 *   delete:
 *      summary: Delete a single case based on the unique businessKey
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
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
router.delete("/:businessKey", caseController.deleteCase.bind(caseController));

/**
 * @swagger
 * /api/cases/{businessKey}/evidence:
 *   post:
 *      summary: Upload file evidence to a case
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case to upload the evidence to
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      requestBody:
 *        required: true
 *        content:
 *          multipart/form-data:
 *            schema:
 *              type: object
 *              properties:
 *                descriptions:
 *                  type: array
 *                  items:
 *                    type: string
 *                    description: textual description of the piece of evidence
 *                    example: Bank video showing acts of terrorism
 *                tags:
 *                  type: array
 *                  items:
 *                    type: string
 *                    example: voice
 *                titles:
 *                  type: array
 *                  items:
 *                    type: string
 *                    example:   title
 *                sources:
 *                  type: array
 *                  items:
 *                    type: string
 *                    example: source
 *                comments:
 *                  type: array
 *                  items:
 *                    type: string
 *                    example: comment
 *                datetimes:
 *                  type: array
 *                  items:
 *                    type: string
 *                    example: "2025-09-01T00:00:00.000Z"
 *                files:
 *                  type: array
 *                  items:
 *                    type: string
 *                    format: binary
 *
 *
 *      responses:
 *        204:
 *          description: Updated!
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.post(
  "/:businessKey/evidence",
  upload.any(),
  caseController.addEvidence.bind(caseController)
);

/**
 * @swagger
 * /api/cases/{businessKey}:
 *   put:
 *      summary: Update the case variables without advancing a step - USE WITH CAUTION
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case to update the variables
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      requestBody:
 *        required: true
 *        content:
 *          application/json:
 *            schema:
 *              $ref: '#/components/schemas/newCase'
 *      responses:
 *        204:
 *          description: Updated!
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.put("/:businessKey", caseController.updateCase.bind(caseController));

/**
 * @swagger
 * /api/cases/{businessKey}/evidence/{evidenceId}:
 *   get:
 *      summary: Retrieve a single evidence file
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
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
router.get(
  "/:businessKey/evidence/:evidenceId",
  caseController.getEvidence.bind(caseController)
);

/**
 * @swagger
 * /api/cases/{businessKey}/evidence/{evidenceId}:
 *   delete:
 *      summary: Delete a single evidence file
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
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
 *          description: The unique id of the evidence file to delete
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        205:
 *          description: Deleted!
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.delete(
  "/:businessKey/evidence/:evidenceId",
  caseController.deleteEvidence.bind(caseController)
);

/**
 * @swagger
 * /api/cases/{businessKey}/close:
 *   put:
 *      summary: Mark a case as closed
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case to close
 *          schema:
 *            type: string
 *            format: uuid-v4
 *      responses:
 *        204:
 *          description: Updated!
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.put(
  "/:businessKey/close",
  caseController.closeCase.bind(caseController)
);

/**
 * @swagger
 * /api/cases/{businessKey}/advance:
 *   post:
 *      summary: Advance a single step in a case. USE WITH CAUTION
 *      security:
 *        - BearerAuth: []
 *        - InternalWSAuth: []
 *      tags: [cases]
 *      parameters:
 *        - name: businessKey
 *          in: path
 *          required: true
 *          example: 12345678-1234-1234-1234-1234567890ab
 *          description: The unique businessKey of the case to close
 *          schema:
 *            type: string
 *            format: uuid-v4
 *        - name: step
 *          in: query
 *          required: true
 *          example: 3
 *          description: The number of the step to advance
 *        - name: template
 *          in: query
 *          required: true
 *          example: uc_3
 *          description: The use-case template of the case
 *      requestBody:
 *        content:
 *          application/json:
 *            schema:
 *              type: object
 *      responses:
 *        204:
 *          description: Updated!
 *        500:
 *          content:
 *            application/json:
 *              schema:
 *                $ref: '#/components/schemas/error500'
 */
router.post(
  "/:businessKey/advance",
  caseController.advanceCase.bind(caseController)
);

export default router;
