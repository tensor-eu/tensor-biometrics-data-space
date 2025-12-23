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
import * as dotenv from "dotenv";
import merge from "deepmerge";
dotenv.config({ path: "./api.env" });

export class CaseService {
  camundaHost = process.env.CAMUNDA_HOST || "localhost";
  camundaPort = process.env.CAMUNDA_PORT || 9080;

  analysisParams = [
    "match",
    "request",
    "response",
    "data-exchange",
    "offline-analysis",
  ];

  /**
   * Method that creates a new case object and stores it in the CMS
   * @param {*} newCase - The variables object that will be contained in the newly initialized case
   * @param {string} caseType - The template name that the newly created case will follow
   * @returns The newly created case object, that has already advanced to step 2
   */
  async createCase(newCase, caseType) {
    const newProcess = await (
      await axios.post(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/process-definition/key/${caseType}/start`,
        {
          variables: newCase,
          businessKey: newCase.businessKey.value,
        }
      )
    ).data;
    Logger.log({ newCase: newProcess });
    Logger.log("Process created successfully");

    const tasks = await (
      await axios.get(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task?processInstanceBusinessKey=${newProcess.businessKey}&withoutTenantId=false&includeAssignedTasks=false&assigned=false&unassigned=false&withoutDueDate=false&withCandidateGroups=false&withoutCandidateGroups=false&withCandidateUsers=false&withoutCandidateUsers=false&active=false&suspended=false&variableNamesIgnoreCase=false&variableValuesIgnoreCase=false`
      )
    ).data;
    Logger.log({ newTask: tasks[0] });

    const complete = await (
      await axios.post(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${tasks[0].id}/complete`,
        {
          variables: newCase,
        }
      )
    ).data;
    Logger.log("Task completed successfully");

    return newProcess;
  }

  /**
   * Method that returns all the case objects contained in the CMS (Or a subset based on various filters)
   * @returns {Promise<Array>} An array of cases object, containing both case variables and auxilliary task fields
   */
  async getAllCases(firstResult = 0, itemsPerPage = 100000, filter = null) {
    const tasks = await (
      await axios.get(
        `http://${this.camundaHost}:${
          this.camundaPort
        }/engine-rest/task?sortBy=created&sortOrder=desc&processDefinitionKeyIn=process_uc3&firstResult=${firstResult}&maxResults=${itemsPerPage}${
          filter
            ? "&processVariables=" +
              encodeURI(filter) +
              "&variableNamesIgnoreCase=true&variableValuesIgnoreCase=true"
            : ""
        }`
      )
    ).data;
    const result = [];

    for (let task of tasks) {
      const caseVars = await (
        await axios.get(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${task.id}/form-variables?deserializeValues=true`
        )
      ).data;

      //add only tasks that are associated with the use cases
      if (caseVars.uc_template) {
        const { id, name, created, processInstanceId } = task;
        result.push({
          task: { id, name, created, processInstanceId },
          caseVars,
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
        }/engine-rest/task/count?processDefinitionKeyIn=process_uc3${
          filter
            ? "&processVariables=" +
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
    const tasks = await (
      await axios.get(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task?withoutDueDate=false&sortBy=created&sortOrder=desc&processInstanceBusinessKey=${businessKey}`
      )
    ).data;
    let result;

    if (tasks.length > 1) {
      //This should never happen
      Logger.warn("BusinessKey returns more than 1 cases: " + tasks.length);
    }
    for (let task of tasks) {
      const caseVars = await (
        await axios.get(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${task.id}/form-variables?deserializeValues=true`
        )
      ).data;

      //add only tasks that are associated with the use cases
      if (caseVars.uc_template) {
        const { id, name, created, processInstanceId } = task;
        result = { task: { id, name, created, processInstanceId }, caseVars };
      }
    }
    return result;
  }

  /**
   * Method that updates a case, with the addition of file evidence
   * @param {string} businessKey - the businessKey (external id) of the specific case
   * @param {*} evidenceData - An array containing the necessary fields to link the evidence files to the case
   */
  async addCaseEvidence(businessKey, evidenceData) {
    const currentCase = await this.getCase(businessKey);
    let complete;
    //if the case is not on the proiper step to collect evidence
    //Add the evidence nonetheless, but do not advance the current step
    if (!currentCase.task.name.match(/Step2/)) {
      //Merge previous evidence with the new one to prevent overwriting
      evidenceData.evidence.value = evidenceData.evidence.value.concat(
        currentCase.caseVars.evidence.value
      );
      complete = await (
        await axios.post(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${currentCase.task.id}/variables`,
          {
            modifications: evidenceData,
          }
        )
      ).data;
    } else {
      complete = await (
        await axios.post(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${currentCase.task.id}/complete`,
          {
            variables: evidenceData,
          }
        )
      ).data;
    }
    Logger.log("Task completed successfully");
    return complete;
  }

  /**
   * Method that updates a case with a general variable, but does not advance the case step
   * @param {string} businessKey - the businessKey (external id) of the specific case
   * @param {*} data - A key-value map object containing the arbitary variable(s) to update
   */
  async updateCase(businessKey, data, retry = 0, maxRetries = 5) {
    const currentCase = await this.getCase(businessKey);
    try {
      console.log(data);
      const complete = await (
        await axios.post(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/process-instance/${currentCase.task.processInstanceId}/variables`,
          { modifications: data }
        )
      ).data;
      Logger.log("Task completed successfully");
      return complete;
    } catch (error) {
      if (retry >= maxRetries) {
        return "Cannot update case in camunda backend. Max retries exceeded";
      }
      Logger.error(
        `Attempt to update case failed with error`,
        error,
        "retrying..."
      );
      //wait 2 secs before retrying
      await new Promise((resolve) => setTimeout(resolve, 2000));
      return this.updateCase(businessKey, data, ++retry);
    }
  }

  /**
   * Method that advances a case without any input, until it's closed by the CMS
   * @param {string} businessKey - the businessKey (external id) of the specific case
   */
  async closeCase(businessKey) {
    let currentCase = await this.getCase(businessKey);
    do {
      //advance the case wiithout any new variables
      await (
        await axios.post(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${currentCase.task.id}/complete`,
          {}
        )
      ).data;
      currentCase = await this.getCase(businessKey);
    } while (currentCase);
    return currentCase;
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
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/process-instance/${currentCase.task.processInstanceId}?skipCustomListeners=false&skipIoMappings=false&skipSubprocesses=false&failIfNotExists=true`
      )
    ).data;
    fs.rmSync(`uploads/${businessKey}`, { recursive: true, force: true });
    return delprocess;
  }

  /**
   * Method that advances a case and updates the intermediate_results case variable
   * @param {string} businessKey - The businessKey of the case to advance
   * @param {*} results - The new results that were produced as part of step X
   * @param {{template:string, step:string}} caseParams - An object containing the number of the case step that was processed and the template of the use_case
   */
  async advanceCase(businessKey, results, caseParams) {
    let step = caseParams.step;
    const task = await (
      await axios.get(
        `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task?processInstanceBusinessKey=${businessKey}&withoutTenantId=false&includeAssignedTasks=false&assigned=false&unassigned=false&withoutDueDate=false&withCandidateGroups=false&withoutCandidateGroups=false&withCandidateUsers=false&withoutCandidateUsers=false&active=false&suspended=false&variableNamesIgnoreCase=false&variableValuesIgnoreCase=false`
      )
    ).data[0];
    let taskId;
    if (task) {
      taskId = task.id;
      const caseVars = await (
        await axios.get(
          `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${taskId}/variables?deserializeValues=true`
        )
      ).data;
      //if the case is in the correct, update the case
      if (
        caseVars.uc_template &&
        caseVars.uc_template.value == caseParams.template
      ) {
        caseVars.intermediate_results = caseVars.intermediate_results || {
          value: {},
        };
        if (!caseVars.intermediate_results.value[step]) {
          console.log("overwritting empty results in step " + step);
          console.log("results to write:", results);
          caseVars.intermediate_results.value[step] = results;
        } else if (Array.isArray(caseVars.intermediate_results.value[step])) {
          console.log("pushing more resuts in step " + step);
          caseVars.intermediate_results.value[step].push(results);
        } else {
          caseVars.intermediate_results.value[step] = merge(
            caseVars.intermediate_results.value[step],
            results
          );
        }
        delete caseVars.intermediate_results.type; //resubmitting a previous object type crashes the Camunda backend

        if (task.name.toLowerCase().includes(caseParams.step)) {
          const complete = await (
            await axios.post(
              `http://${this.camundaHost}:${this.camundaPort}/engine-rest/task/${taskId}/complete`,
              {
                variables: {
                  intermediate_results: caseVars.intermediate_results,
                },
              }
            )
          ).data;
          return true;
        }
        return;
      } else {
        console.log(
          `Cannot update case with businessKey: ${businessKey} due to template or step mismatch`
        );
        return false;
      }
    } else {
      throw Error(`Case with businessKey: ${businessKey} does not exist`);
    }
  }

  /**
   * Method that adds arbitrary data to an intermediate_result step and converts it to an array
   * @param {*} data
   * @param {string} businessKey
   * @param {string} step
   * @returns
   */
  async insertResults(data, businessKey, step) {
    const currCase = await this.getCase(businessKey);
    if (!currCase) {
      console.error(
        "Cannot insert result on case with businesskey " +
          businessKey +
          "case does not exist"
      );
      return;
    }
    const newIntResults = currCase.caseVars.intermediate_results || {
      value: {},
    };
    if (newIntResults) delete newIntResults.type; //resubmitting a previous object type crashes the Camunda backend

    if (Array.isArray(newIntResults.value[step])) {
      newIntResults.value[step].push(data);
    } else if (newIntResults.value[step]) {
      newIntResults.value[step] = merge(newIntResults.value[step], data);
      //if an old value exists but is not an array, merge the objects?
      //newIntResults.value[step].push(currCase.caseVars.intermediate_results.value[step])
    } else {
      newIntResults.value[step] = data;
    }
    // newIntResults.value[step].push(data)
    const complete = await this.updateCase(businessKey, {
      intermediate_results: newIntResults,
    });
    return complete;
  }
}

export default new CaseService();
