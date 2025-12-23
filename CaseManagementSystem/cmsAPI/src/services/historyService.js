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

"use strict";
import axios from "axios";
import fs from "fs";
import Logger from "js-logger";
Logger.setLevel(Logger.INFO);

export class HistoryService {
  camundaHost = process.env.CAMUNDA_HOST || "localhost";
  camundaPort = process.env.CAMUNDA_PORT || 8091;

  /**
   * Method that returns all the case objects contained in the CMS (Or a subset based on various filters)
   * @returns A array of cases object, containing both case variables and auxilliary task fields
   */
  async getAllCases(firstResult = 0, itemsPerPage = 100000, filter = null) {
    const processes = await (
      await axios.get(
        `http://${this.camundaHost}:${
          this.camundaPort
        }/engine-rest/history/process-instance?finished=true&sortBy=startTime&sortOrder=desc&processDefinitionKeyIn=process_uc3&firstResult=${firstResult}&maxResults=${itemsPerPage}${
          filter
            ? "&processVariables=" +
              encodeURI(filter) +
              "&variableNamesIgnoreCase=true&variableValuesIgnoreCase=true"
            : ""
        }`
      )
    ).data;
    const result = [];

    for (let process of processes) {
      const caseVars = await (
        await axios.get(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/history/variable-instance?processInstanceId=${process.id}`
        )
      ).data;

      //add only tasks that are associated with the use cases
      const formattedVars = this.formatCaseVars(caseVars);
      if (formattedVars.uc_template) {
        const {
          id,
          businessKey,
          processDefinitionKey,
          startTime,
          endTime,
          removalTime,
          state,
        } = process;
        result.push({
          task: {
            id,
            businessKey,
            processDefinitionKey,
            startTime,
            endTime,
            removalTime,
            state,
          },
          caseVars: formattedVars,
        });
      }
    }
    return result;
  }

  /**
   * Method that returns the number of all cases stored in the Camunda BPMN backend
   * @returns {Promise<Number>} The number all cases in the camunda BPMN backend
   */
  async getAllCasesCount(filter = null) {
    const taskNum = await (
      await axios.get(
        `http://${this.camundaHost}:${
          this.camundaPort
        }/engine-rest/history/process-instance/count?finished=true&processDefinitionKeyIn=process_uc3${
          filter
            ? "&variables=" +
              encodeURI(filter) +
              "&variableNamesIgnoreCase=true&variableValuesIgnoreCase=true"
            : ""
        }`
      )
    ).data;
    return taskNum.count || 0;
  }

  /**
   * Method that retrieves a case object from the CMS, based on a unique businessKey (external ID)
   * @param {string} businessKey - The unique external ID that describes a single case
   * @returns A single case object, containing both case variables and auxilliary task fields
   */
  async getCase(businessKey) {
    const processes = await (
      await axios.get(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/history/process-instance?sortBy=startTime&sortOrder=desc&finished=true&processInstanceBusinessKey=${businessKey}`
      )
    ).data;
    let result;

    if (processes.length > 1) {
      //This should never happen
      Logger.warn("BusinessKey returns more than 1 cases: " + processes.length);
    }
    for (let process of processes) {
      const caseVars = await (
        await axios.get(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/history/variable-instance?processInstanceId=${process.id}`
        )
      ).data;

      //add only tasks that are associated with the use cases
      const formattedVars = this.formatCaseVars(caseVars);
      if (formattedVars.uc_template) {
        const {
          id,
          businessKey,
          processDefinitionKey,
          startTime,
          endTime,
          removalTime,
          state,
        } = process;
        result = {
          task: {
            id,
            businessKey,
            processDefinitionKey,
            startTime,
            endTime,
            removalTime,
            state,
          },
          caseVars: formattedVars,
        };
      }
    }
    return result;
  }

  /**
   * method that retrieves a single evidence file object from a case
   * @param {*} task - The case object containing the evidence, as retrieved from getCase
   * @param {string} evidenceId - The uuid of the specific piece of evidence to retrieve
   * @returns - The file object corresponding to evidenceId, contained the the "task" case
   */
  getCaseEvidence(task, evidenceId) {
    for (let file of task.caseVars.evidence.value) {
      if (file.id == evidenceId) {
        return file;
      }
    }
    return undefined;
  }

  /**
   * Method that deletes a case from the CMS and removes all the associated data
   * @param {string} businessKey - The businessKey of the case to delete
   */
  async deleteCase(businessKey) {
    const currentCase = await this.getCase(businessKey);
    if (!currentCase) {
      throw Error(`Task with businessKey:${businessKey} does not exist`);
    }
    const delprocess = await (
      await axios.delete(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/history/process-instance/${currentCase.task.id}?failIfNotExists=true`
      )
    ).data;
    fs.rmSync(`uploads/${businessKey}`, { recursive: true, force: true });
    return delprocess;
  }

  /**
   *
   * @param {*} caseVars
   * @returns
   */
  formatCaseVars(caseVars) {
    let caseVarsObj = {};
    for (let caseVar of caseVars) {
      const { type, value, valueInfo, state, createTime } = caseVar;
      caseVarsObj[caseVar.name] = { type, value, valueInfo, state, createTime };
    }
    return caseVarsObj;
  }
}

export default new HistoryService();
