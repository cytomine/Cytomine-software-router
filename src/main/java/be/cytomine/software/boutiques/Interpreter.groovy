package be.cytomine.software.boutiques

/*
 * Copyright (c) 2009-2018. Authors: see NOTICE file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import be.cytomine.client.collections.ProcessingServerCollection
import be.cytomine.client.models.ProcessingServer
import be.cytomine.software.consumer.Main
import be.cytomine.software.exceptions.BoutiquesException
import groovy.json.JsonSlurper

class Interpreter {

    private final int BASE_INDEX = 100

    private def descriptor
    private def inputs

    Interpreter(String filename) {
        if (!validateDescriptor()) {
            throw new BoutiquesException("The descriptor is invalid !")
        }

        descriptor = new JsonSlurper().parse(new File(filename))
        inputs = descriptor."inputs"
    }

    def validateDescriptor() {
        return true
    }

    def buildExecutionCommand(def imageName) {
        String commandLine = descriptor?."command-line"
        if (!commandLine) throw new BoutiquesException("The commandLine is missing !")

        def containerImage = descriptor?."container-image"

        String imageType = containerImage?."type"
        if (!imageType) throw new BoutiquesException("The image type is missing !")

        return "${imageType} run ${imageName} ${commandLine}"
    }

    def getImageName() {
        String image = descriptor?."container-image"?."image"
        if (!image) throw new BoutiquesException("The image name is missing !")

        return image.substring(image.indexOf("/") + 1, image.size())
    }

    def getPullingInformation() {
        def containerImage = descriptor?."container-image"

        String imageType = containerImage?."type"
        if (!imageType) throw new BoutiquesException("The image type is missing !")

        String index = containerImage?."index"
        if (!index) throw new BoutiquesException("The image index is missing !")

        String image = containerImage?."image"
        if (!image) throw new BoutiquesException("The image is missing !")

        return [imageType: imageType, index: index, image: image]
    }

    def parseSoftware() {
        String name = descriptor?."name"

        String defaultProcessingServerName = descriptor?."default-processing-server-name"
        if (!defaultProcessingServerName) throw new BoutiquesException("The default processing server name is missing !")

        Long processingServerId = -1L
        ProcessingServerCollection processingServers = Main.cytomine.getProcessingServerCollection()
        for (int i = 0; i < processingServers.size(); i++) {
            ProcessingServer currentProcessingServer = processingServers.get(i)
            if (currentProcessingServer.getStr("name").trim().toLowerCase() == defaultProcessingServerName.trim().toLowerCase()) {
                processingServerId = currentProcessingServer.getId()
            }
        }

        if (processingServerId == -1L) throw new BoutiquesException("The processing server [${defaultProcessingServerName}] was not found !")

        return ["name": name, "processingServerId": processingServerId]
    }

    def parseParameters() {
        ArrayList parameters = new ArrayList()
        int index = BASE_INDEX

        inputs.each {
            def currentParameter = parseParameter(it)
            currentParameter["index"] = index

            parameters.add(currentParameter)

            index += 100
        }

        return parameters
    }

    def parseParameter(def json) {
        String name = json?."name"
        if (!name) throw new BoutiquesException("The parameter name is missing !")

        String type = json?."type"
        if (!type) throw new BoutiquesException("The parameter type is missing !")

        boolean required = json?."optional" as boolean

        String defaultValue = json?."default-value"
        if (!defaultValue) defaultValue = ""

        boolean setByServer = json?."set-by-server" as Boolean

        boolean serverParameter = json?."server-parameter" as Boolean

        String minimum = json?."minimum" as String

        String maximum = json?."maximum" as String

        String equals = json?."equals" as String

        String inside = json?."in" as String

        String uri = json?."uri"

        String uriPrintAttribut = json?."uri-print-attribut"

        String uriSortAttribut = json?."uri-sort-attribut"

        return ["name": name,
                "type": type,
                "required": required,
                "defaultValue": defaultValue,
                "setByServer": setByServer,
                "serverParameter": serverParameter,
                "minimum": minimum,
                "maximum": maximum,
                "equals": equals,
                "in": inside,
                "uri": uri,
                "uriPrintAttribut": uriPrintAttribut,
                "uriSortAttribut": uriSortAttribut]
    }

}
