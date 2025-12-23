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
import caseService from "../services/caseService";
import { v4 as uuidv4 } from "uuid";
import fs from "fs";

export class CaseController {
  // ENUM binding use case templates to camunda process keys
  caseTypes = {
    uc_3: "process_uc3",
  };

  /**
   * @param {import ('express').Request} req
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async createCase(req, res, next) {
    const newCase = req.body;

    if (!(newCase.uc_template.value in this.caseTypes)) {
      return res
        .status(400)
        .send({
          error: `Use case ${newCase.uc_template.value} not supported by the CMS`,
        });
    }
    try {
      const caseType = this.caseTypes[newCase.uc_template.value];
      newCase.businessKey = { value: uuidv4() };
      const newProcess = await caseService.createCase(newCase, caseType);

      return res.status(201).send({ businessKey: newProcess.businessKey });
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }

  /**
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

      const tasks = await caseService.getAllCases(
        startItem,
        itemsPerPage,
        filter
      );
      for (let task of tasks) {
        if (task.caseVars.intermediate_results) {
          delete task.caseVars.intermediate_results;
        }
      }

      const totalCaseNum = await caseService.getAllCasesCount(filter);
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
   * Method that retrieves a single case from the CMS, based on businessKey param
   * @param {import ('express').Request} req - Must contain a 'businessKey' path param
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async getCase(req, res, next) {
    const businessKey = req.params.businessKey;

    try {
      const task = await caseService.getCase(businessKey);
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
   * Method that deletes a single case from the CMS, based on businessKey
   * @param {import ('express').Request} req -  Must contain a 'businessKey' path param
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   */
  async deleteCase(req, res, next) {
    const businessKey = req.params.businessKey;

    try {
      const process = await caseService.deleteCase(businessKey);
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
  async addEvidence(req, res, next) {
    const evidence = req.files;
    const descriptions = req.body.descriptions;
    const tags = req.body.tags;
    const titles = req.body.titles;
    const sources = req.body.sources;
    const comments = req.body.comments;
    const datetimes = req.body.datetimes;
    const businessKey = req.params.businessKey;

    try {
      const task = await caseService.getCase(businessKey);
      const descriptionsArray = descriptions
        ? descriptions.toString().split(",")
        : [];
      const tagsArray = tags ? tags.toString().split(",") : [];
      const titlesArray = titles ? titles.toString().split(",") : [];
      const sourcesArray = sources ? sources.toString().split(",") : [];
      const commentsArray = comments ? comments.toString().split(",") : [];
      const datetimesArray = datetimes ? datetimes.toString().split(",") : [];
      if (!task) {
        return res
          .status(404)
          .send({ error: `Case with businessKey ${businessKey} not found` });
      }
      if (
        !descriptionsArray ||
        !evidence ||
        descriptionsArray.length != evidence.length
      ) {
        return res
          .status(400)
          .send({ error: `Evidence files do not match evidence descriptions` });
      }
      if (tagsArray.length != evidence.length) {
        return res
          .status(400)
          .send({ error: `Tags do not match evidence descriptions` });
      }
      const evidenceData = [];
      // @ts-ignore   multer.files CAN be a dictionary but in this use-case will always be an array
      for (let i = 0; i < evidence.length; i++) {
        let fileObj = {
          id: uuidv4(),
          type: evidence[i].mimetype,
          createdAt: new Date(),
          url: evidence[i].path,
          description: descriptionsArray[i],
          tag: tagsArray[i],
          title: titlesArray[i],
          source: sourcesArray[i],
          comment: commentsArray[i],
          datetime: datetimesArray[i],
          size: evidence[i].size,
        };
        evidenceData.push(fileObj);
      }
      const updated = await caseService.addCaseEvidence(businessKey, {
        evidence: { value: evidenceData },
      });
      return res.status(204).send(updated);
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
  async updateCase(req, res, next) {
    const variables = req.body;
    const businessKey = req.params.businessKey;

    try {
      const task = await caseService.getCase(businessKey);
      if (!task) {
        return res
          .status(404)
          .send({ error: `Case with businessKey ${businessKey} not found` });
      }
      if (!variables || Object.keys(variables).length == 0) {
        return res.status(400).send({ error: `No variables to update` });
      }
      if (
        Object.keys(variables).indexOf("businessKey") >= 0 ||
        Object.keys(variables).indexOf("uc_template") >= 0
      ) {
        return res
          .status(400)
          .send({
            error: `DO NOT update 'uc_template' or 'businessKey'. Please create a new case`,
          });
      }

      const updated = await caseService.updateCase(businessKey, variables);
      return res.status(204).send(updated);
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
      const task = await caseService.getCase(businessKey);
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
      const file = caseService.getCaseEvidence(task, evidenceId);
      if (file) {
        const filePath = file.url;
        const mimetype = file.type;
        //const fileName = filePath.split("\\")[filePath.split("\\").length-1]

        //res.setHeader('Content-disposition', 'attachment; filename='+fileName);
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

  /**
   *
   * @param {import ('express').Request} req
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   * @returns
   */
  async deleteEvidence(req, res, next) {
    const businessKey = req.params.businessKey;
    const evidenceId = req.params.evidenceId;

    try {
      const task = await caseService.getCase(businessKey);
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
      const file = caseService.getCaseEvidence(task, evidenceId);
      if (file) {
        const filePath = file.url;
        fs.rmSync(filePath);
        //remove the deleted evidence from the case
        task.caseVars.evidence.value = task.caseVars.evidence.value.filter(
          function (item) {
            return item.id !== evidenceId;
          }
        );
        delete task.caseVars.evidence.type;
        caseService.updateCase(businessKey, {
          evidence: task.caseVars.evidence,
        });
        return res.status(205).send();
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

  /**
   *
   * @param {import ('express').Request} req
   * @param {import ('express').Response} res
   * @param {import ('express').NextFunction} next
   * @returns
   */
  async closeCase(req, res, next) {
    const businessKey = req.params.businessKey;

    try {
      let task = await caseService.getCase(businessKey);
      if (!task) {
        return res
          .status(404)
          .send({ error: `Case with businessKey ${businessKey} not found` });
      }
      await caseService.closeCase(businessKey);
      return res.status(205).send();
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
  async advanceCase(req, res, next) {
    const step = (req.query.step?.toString() || "").toLowerCase();
    if (caseService.analysisParams.indexOf(step) == -1) {
      return res
        .status(400)
        .send(`${step} is not a recognised analysis step to advance`);
    }
    const businessKey = req.params.businessKey;
    const results = req.body;
    const caseParams = {
      template: req.query.template.toString(),
      step: req.params.step,
    };

    try {
      let updateCase = await caseService.advanceCase(
        businessKey,
        results,
        caseParams
      );
      if (updateCase) {
        Logger.log(`Case ${businessKey} advanced ${step} step`);
      } else if (results && !updateCase) {
        caseService.insertResults(results, businessKey, step);
      }
      return res.status(200).send();
    } catch (err) {
      Logger.log(err);
      return res
        .status(500)
        .send({ error: err.message || "Internal Server error" });
    }
  }
}

export default new CaseController();
