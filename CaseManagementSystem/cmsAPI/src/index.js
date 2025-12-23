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

import express from "express";
import dotenv from "dotenv";
import cors from "cors";
import caseRouter from "./routes/caseRouter";
import uc3Router from "./routes/uc3Router";
import historyRouter from "./routes/historyRouter";
import swaggerUI from "swagger-ui-express";
import swaggerDocs, { swaggerOptions } from "./services/swaggerService";
import Logger from "js-logger";
import * as fs from "fs/promises";
import { authChecker, strategy } from "./middleware/auth";
import passport from "passport";

dotenv.config({ path: "cmsAPI/api.env" });
Logger.useDefaults();
Logger.setLevel(Logger.INFO);
const API_PORT = process.env.API_PORT || 3001;

const app = express();
passport.use(strategy);
app.use(cors());
app.use(express.json({ limit: "100mb" }));
app.use(express.urlencoded({ extended: true }));
app.use(express.static("public")); //statically serve anything in the public folder (for UI resources)

app.use("/api/cases", authChecker(passport), caseRouter);
app.use("/api/cases", authChecker(passport), uc3Router);
app.use("/api/history", authChecker(passport), historyRouter);

fs.writeFile("../api.json", JSON.stringify(swaggerDocs)); //auto-generate the swagger api json file
app.use(
  "/api-docs",
  swaggerUI.serve,
  swaggerUI.setup(swaggerDocs, swaggerOptions)
);

let server = app.listen(API_PORT, () => {
  Logger.info(`App running on port ${API_PORT}...`);
});

export default app;
