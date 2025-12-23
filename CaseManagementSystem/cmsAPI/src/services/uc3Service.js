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

'use strict'
import axios from "axios"
import Logger from "js-logger"
import { v4 as uuidv4 } from 'uuid'
import * as dotenv from 'dotenv'
import caseService from "./caseService"
dotenv.config({"path": "./api.env"})
Logger.setLevel(Logger.INFO)

export class Uc3Service {
    camundaHost = process.env.CAMUNDA_HOST || 'localhost'
    camundaPort = process.env.CAMUNDA_PORT || 8091
    dataspace = process.env.DATASPACE
    dataspacePort = process.env.DATASPACE_PORT

    /**
     * Method that retrieves the encrypted hash index of a request image by the indexer module 
     * @param {string} businessKey - The businessKey of the case the match operation is called on
     * @param {Array<{type:string, hashIndex:Number}>} imageId - The evidence id of the image, as stored in the createEvidence step 
     * @returns The hash value of the request image
     */
    async requestHash(businessKey, imageId) {
        const url = `http://localhost:${process.env.API_PORT || 3001}/api/cases/${businessKey}/evidence/${imageId}`
        //const hash = await (await axios.post(`http://${this.indexer}:${this.indexerPort}/request-hash/`, {url})).data
        const hash = uuidv4()
        
        return hash
    }

    /**
     * Method that forwards the match request to the dataspace API and returns the results
     * @param {string} authHeader - The authorization header containing the JWT to be sent to the module
     * @param {*} query - An object of biometric URLs to be matched against in the dataspace module
     * @returns The matching score of each submitted biometric as submitted by the dataspace
     */
    async match(authHeader, query) {
        const matchRes = await (await axios.post(`http://${this.dataspace}:${this.dataspacePort}/match`, query, {headers:{Authorization: authHeader}})).data
        return matchRes
    }

    /**
     * Method that forwards the create dataRequest request to the dataspace API and returns the results
     * @param {string} businessKey - The businessKey of the case the dataRequest operation is called on
     * @param {*} hashObj - A object containing the indexed hash of the requested biometric
     * @param {String} businessKey - The businessKey of the case to advance when the response comes back from the cvonnector 
     * @param {string} [solidToken] - The solid oidc token used to interact with the DSP
     * @returns The hex data of the generated blockchain transaction, ready to be signed by the user
     */
    async createRequest(hashObj, businessKey, solidToken=null) {
        hashObj["businessKey"] = businessKey
        const requestRes = (await axios.post(
            `http://${this.dataspace}:${this.dataspacePort}/dsp-request`,
            hashObj,
            {headers: {"solidToken": solidToken}}
        )).data
        return requestRes
    }

    /**
     * Method that forwards the create dataResponse request to the dataspace API and returns the results
     * @param {*} responseObj - An object containing the necessary fileds for the creation of a data response as defined by the DSP
     * @returns The hex data of the generated blockchain transaction, ready to be signed by the user
     */
    async createResponse(responseObj) {
        const responseRes = await (await axios.post(`http://${this.dataspace}:${this.dataspacePort}/dsp-response`, responseObj)).data
        return responseRes
    }

    /**
     * Method that forwards the create dataResponse request to the dataspace API and returns the results
     * @param {string} businessKey - the businessKey of the case to advance when the response arrives
     * @param {Array<{type: string;hashIndex: Number;}>} response - An object containing the indexed hash of the response
     * @returns The hex data of the generated blockchain transaction, ready to be signed by the user

     */
    async receiveResponse(response, businessKey) {
        return response
    }

    /**
     * Method that retrieves the actual data exchanged between the provider and the requestor
     * @param {string} businessKey - The businessKey of the case the dataExchange operation is called on
     * @param {{hashIndex:Number, provider:string, businessKey:string}} exchObj - An object containing the id of the data provider's connector
     * @returns The file(s) requested by the data provider
     */
    async dataExchange(exchObj, businessKey) {
        exchObj.businessKey = businessKey
        const responseRes = await axios.post(`http://${this.dataspace}:${this.dataspacePort}/data-exchange`, exchObj)
        
        const updateCase = await caseService.advanceCase(businessKey, {timestamp: Date.now(), status: responseRes.status}, {template:'uc_3', step:'data-exchange'})
        if (updateCase){
            Logger.log(`Case ${businessKey} advanced exchange step`)
        }
        return responseRes
    }

    /**
     * Method that marks the completion of the internal analysis period by a LEA
     * @param {string} businessKey - The businessKey of the case the dataExchange operation is called on
     */
    async completeAnalysis(businessKey) {
        
        const updateCase = await caseService.advanceCase(businessKey, {timestamp: Date.now(), status: "OK!"}, {template:'uc_3', step:'offline-analysis'})
        if (updateCase){
            Logger.log(`Case ${businessKey} advanced offline analysis step`)
        }
        return
    }
}

export default new Uc3Service()