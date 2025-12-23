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
import Logger from "js-logger";
import historyService from "../services/historyService";
import fs from "fs";

export class HistoryController {
  // ENUM binding use case templates to camunda process keys
  caseTypes = {
    uc_3: "process_uc3",
  };

  /**
   * Method that retrieves all closed cases from the CMS
   * @param {import ('express').Request} req
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async getCases(req, res, next) {
    const page = Number(req.query?.page) || 0;
    const itemsPerPage = Number(req.query?.itemsPerPage) || 100000;
    const filter = req.query?.filter || null;
    try {
      const startItem = page * itemsPerPage;

      const tasks = await historyService.getAllCases(
        startItem,
        itemsPerPage,
        filter
      );
      for (let task of tasks) {
        if (task.caseVars.intermediate_results) {
          delete task.caseVars.intermediate_results;
        }
      }

      const totalCaseNum = await historyService.getAllCasesCount(filter);
      const totalPages = Math.ceil(totalCaseNum / itemsPerPage);

      const returnValue = {
        page: page,
        hasNextPage: totalPages > page + 1,
        totalPages: totalPages,
        totalItems: totalCaseNum,
        data: tasks,
      };

      return res.status(200).send(returnValue);
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }

  /**
   * Method that retrieves a single closed case from the CMS, based on businessKey param
   * @param {import ('express').Request} req - Must contain a 'businessKey' path param
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async getCase(req, res, next) {
    const businessKey = req.params.businessKey;

    try {
      const task = await historyService.getCase(businessKey);
      if (task) return res.status(200).send(task);
      else
        return res
          .status(404)
          .send({ error: `Case with businessKey ${businessKey} not found` });
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }

  /**
   * Method that deletes a single closed case from the CMS, based on businessKey
   * @param {import ('express').Request} req -  Must contain a 'businessKey' path param
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async deleteCase(req, res, next) {
    const businessKey = req.params.businessKey;

    try {
      const process = await historyService.deleteCase(businessKey);
      return res.status(204).send(process);
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }

  /**
   *
   * @param {import ('express').Request} req
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   * @returns
   */
  async getEvidence(req, res, next) {
    const businessKey = req.params.businessKey;
    const evidenceId = req.params.evidenceId;

    try {
      const task = await historyService.getCase(businessKey);
      if (!task) {
        return res
          .status(404)
          .send({ error: `Case with businessKey ${businessKey} not found` });
      }
      if (!task.caseVars.evidence) {
        return res
          .status(400)
          .send({
            error: `Case ${businessKey} does not contain any evidence files`,
          });
      }
      const file = historyService.getCaseEvidence(task, evidenceId);
      if (file) {
        const filePath = file.url;
        const mimetype = file.type;
        res.setHeader("Content-type", mimetype);

        var filestream = fs.createReadStream(filePath);
        return filestream.pipe(res);
      } else {
        return res
          .status(404)
          .send({
            error: `Case with businessKey ${businessKey} does not contain evidence with id ${evidenceId}`,
          });
      }
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }
}

export default new HistoryController();
