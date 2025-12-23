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
//@ts-nocheck
import path, {dirname} from "path"
import swaggerJSDoc from 'swagger-jsdoc'
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));

const swaggerOpts = {
    swaggerDefinition: {
        openapi: '3.0.0',
        info: {
            title:'Tensor Case Management System API',
            version:'1.0.0',
            license: {
                name: 'Licensed Under EUPL-1.2',
                url: 'https://spdx.org/licenses/EUPL-1.2.html',
            },
        },
    },
    apis:[path.join(__dirname,'../routes/**.js')],
}

export var swaggerOptions = {
    customSiteTitle: "TensorCase Management System API",
    customfavIcon: "/favicon.ico",
    customCss: `.topbar-wrapper img {content:url(\'/img/tensor-sm.png\'); height:80px; width:auto;}
                .swagger-ui .topbar { background-color: #7ac1f0 }`
}


export default swaggerJSDoc(swaggerOpts);