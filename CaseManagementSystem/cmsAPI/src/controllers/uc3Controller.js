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
import Logger from "js-logger"
import uc3Service from "../services/uc3Service"
import dotenv from 'dotenv'
import caseService from "../services/caseService"
dotenv.config({path: 'api.env'})

export class Uc3Controller{

    /**
     * @param {import ('express').Request} req
     * @param {import ('express').Response} res
     * @param {import ('express').NextFunction} next
     */
    async match(req, res, next) {
        const query = req.body
        const businessKey = req.params.businessKey

        try {
            const biometricURLs = this.transformIdsToUrls(query, businessKey)
            const matchResult = await uc3Service.match(req.headers.authorization, biometricURLs)

            const idArray = [query.faceId, query.voiceId, query.fingerprintId]
            const updateCase = await caseService.advanceCase(businessKey,{[idArray.toString()] :matchResult},  {template:'uc_3', step:'matching'})
            if (updateCase){
                Logger.log(`Case ${businessKey} advanced matching step`)
            }
            else if(matchResult && !updateCase){
                caseService.insertResults({[idArray.toString()] :matchResult}, businessKey, 'matching')
            }
            return res.status(200).send(matchResult)
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

     /**
     * @param {import ('express').Request} req
     * @param {import ('express').Response} res
     * @param {import ('express').NextFunction} next
     */
    async createRequest(req, res, next) {
        const query = req.body
        const businessKey = req.params.businessKey

        try {
            const biometricURLs = this.transformIdsToUrls(query, businessKey)
            const requestResult = await uc3Service.createRequest(biometricURLs, businessKey)

            const idArray = [query.faceId, query.voiceId, query.fingerprintId]
            const updateObj = {[idArray.toString()] : [requestResult]}
            const updateCase = await caseService.advanceCase(businessKey, updateObj, {template:'uc_3', step: 'request'})
            if (updateCase){
                Logger.log(`Case ${businessKey} advanced request step`)
            }
            else if(requestResult && !updateCase){
                caseService.insertResults({[idArray.toString()] :[requestResult]}, businessKey, 'request')
            }
            return res.status(200).send(requestResult)
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

    /**
    * @param {import ('express').Request} req
    * @param {import ('express').Response} res
    * @param {import ('express').NextFunction} next
    */
    async createResponse(req, res, next) {
        const indexObj = req.body
        try {
            const responseResult = await uc3Service.createResponse(indexObj)
            //const response = await caseService.forceInsertResults(responseResult, "", 3)
            return res.status(200).send(responseResult)
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

    /**
    * @param {import ('express').Request} req
    * @param {import ('express').Response} res
    * @param {import ('express').NextFunction} next
    */
    async receiveResponse(req, res, next) {

        const {response, request} = req.body
        //copy resIndex to response for easy dashboard access
        response.resIndex = request.resIndex

        try {
            const cases = (await caseService.getAllCases()).filter(function(task) {return task.caseVars.uc_template.value == "uc_3"})

            let businessKey
            console.log(`Searching ${cases.length} cases to place response`)
            let requests = []
            let evidenceIndex
            caseLoop:
            for(let currCase of cases){
                if(currCase.caseVars.intermediate_results && currCase.caseVars.intermediate_results.value && (typeof currCase.caseVars.intermediate_results.value['request']) === "object"){
                    let evidenceGroups = currCase.caseVars.intermediate_results.value['request']
                    //get every property and if its value is an array merge these arrays
                    console.log(`Case ${currCase.caseVars.businessKey.value} has object requests`)
                    for(let evidence in evidenceGroups){
                        console.log("for request: "+evidence)
                        if (Array.isArray(evidenceGroups[evidence])){
                            console.log("found array")
                            requests = evidenceGroups[evidence]
                            console.log(`caseBK: ${currCase.caseVars.businessKey.value}`)
                            console.log(`Searching ${requests.length} requests for this case`)
                            evidenceIndex = evidence
                            requestLoop:
                            for(let currRequest of requests){
                                console.log(`currRequest: ${currRequest.resIndex}, ${currRequest.from}, ${currRequest.recipientWebId}`)
                                console.log(`request: ${request.resIndex}, ${request.from}, ${request.toWebId}`)
                                if (currRequest.from.toLowerCase() == request.from.toLowerCase() &&
                                    currRequest.recipientWebId.toLowerCase() == request.toWebId.toLowerCase() && 
                                    currRequest.resIndex.toLowerCase() == request.resIndex.toLowerCase()
                                ){
                                    businessKey = currCase.caseVars.businessKey.value
                                    break caseLoop
                                }
                            }
                        }else{
                            console.log("array not found")
                        }
                    }
                }else{
                    console.log("If you can see this, something went wrong in case selection")
                }
            }
            if (!businessKey){
                console.log("Matched caseBK: " +businessKey)
                return res.status(500).send({error: `Case containing a matching request not found`})
            }
            const responseResult = await uc3Service.receiveResponse(response, businessKey)
            const updateCase = await caseService.advanceCase(businessKey, {[evidenceIndex]:[responseResult]}, {template:'uc_3', step:'response'})
            if (updateCase){
                Logger.log(`Case ${businessKey} advanced response step`)
            }
            else if(responseResult && !updateCase){
                caseService.insertResults({[evidenceIndex]:[responseResult]}, businessKey, 'response')
            }
            
            return res.status(200).send(responseResult)
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

    /**
    * @param {import ('express').Request} req
    * @param {import ('express').Response} res
    * @param {import ('express').NextFunction} next
    */
    async dataExchange(req, res, next) {
        const exchObj = req.body
        const businessKey = req.params.businessKey
 
        try {
            const responseResult = await uc3Service.dataExchange(exchObj, businessKey)
            const updateCase = await caseService.advanceCase(businessKey, {timestamp: Date.now(), status: responseResult.status}, {template:'uc_3', step: 'data-exchange'})
            if (updateCase){
                Logger.log(`Case ${businessKey} advanced data exchange step`)
            }
            else if(responseResult && !updateCase){
                caseService.insertResults({timestamp: Date.now(), status: responseResult.status}, businessKey, 'data-exchange')
            }
            return res.status(responseResult.status).setHeader('Content-Type', responseResult.headers["content-type"].toString()).send(responseResult.data)
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

    /**
    * @param {import ('express').Request} req
    * @param {import ('express').Response} res
    * @param {import ('express').NextFunction} next
    */
    async completeAnalysis(req, res, next) {
        const businessKey = req.params.businessKey
    
        try {
            await uc3Service.completeAnalysis(businessKey)
    
            return res.status(200).send()
        } catch (err) {
            Logger.log(err)
            return res.status(500).send({error: err.message || "Internal Server error"})
        }
    }

    /**
     * Method that transforms the evidence ID params used by the CMS to full URLs used by the connector
     * @param {*} query - The initial object containing the request to forward. Any contained fields named "faceId", "voiceId", "fingerprintId" will be tranformed to the full urls, any other fields will be left untouched
     * @param {string} businessKey - The businessKey of the case this operation takes part as. Needed to form the actual evidence URLs
     * @returns {*} - An object identical to query, except that it ALWAYS contains entries for "sampleFaceUrl", "sampleVoiceUrl", "sampleFingerprintUrl" and any Id fields are substituted with the corresponding URLs
     */
    transformIdsToUrls(query, businessKey){
        const biometricURLs = {"sampleFaceUrl": "", "sampleVoiceUrl": "", "sampleFingerprintUrl": ""}
        const hostUrl = "http://"+ (process.env.API_URL_EXTERNAL || `${process.env.API_HOST}:${process.env.API_PORT}`)

        for(let [key, value] of Object.entries(query)){  
            //special case where the evidence IDs need to be tranformed into URLs
            if(["faceId", "voiceId", "fingerprintId"].includes(key)){
                let modality = key.replace("Id", "")
                modality = modality.charAt(0).toUpperCase()+modality.slice(1)
                biometricURLs[`sample${modality}Url`] =
                    value ? `${hostUrl}/api/cases/${businessKey}/evidence/${value}` : ""
            }else{
                biometricURLs[key] = value
            }
        }
        return biometricURLs
    }        
}

export default new Uc3Controller()