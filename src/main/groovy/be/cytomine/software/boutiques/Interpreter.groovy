package be.cytomine.software.boutiques

/*
 * Copyright (c) 2009-2022. Authors: see NOTICE file.
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

import be.cytomine.client.collections.Collection
import be.cytomine.client.models.ProcessingServer
import be.cytomine.software.exceptions.BoutiquesException
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

@Log4j
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
//        if (!index) throw new BoutiquesException("The image index is missing !")

        String image = containerImage?."image"
        if (!image) throw new BoutiquesException("The image is missing !")

        return [imageType: imageType, index: index, image: image]
    }

    def parseSoftware() {
        String name = descriptor?."name"
        String description = descriptor?."description"

        String defaultProcessingServerName = descriptor?."default-processing-server-name" ?: 'local-server'

        Long processingServerId = -1L
        Long defaultProcessingServerId = -1L
        def minIndex = Integer.MAX_VALUE
        Collection<ProcessingServer> processingServers = Collection.fetch(ProcessingServer.class);
        for (int i = 0; i < processingServers.size(); i++) {
            ProcessingServer currentProcessingServer = processingServers.get(i)
            if (currentProcessingServer.getStr("name").trim().toLowerCase() == defaultProcessingServerName.trim().toLowerCase()) {
                processingServerId = currentProcessingServer.getId()
            }

            if (currentProcessingServer.getInt("index") < minIndex) {
                minIndex = currentProcessingServer.getStr("index")
                defaultProcessingServerId = currentProcessingServer.getId()
            }
        }

        if (processingServerId == -1L) {
            log.warn("The processing server [${defaultProcessingServerName}] was not found, replacing by default server")
            processingServerId = defaultProcessingServerId
        }

        return ["name": name, "description": description, "processingServerId": processingServerId]
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
        String name = json?."id"
        if (!name) throw new BoutiquesException("The parameter name (ID) is missing !")

        String type = json?."type"
        if (!type) throw new BoutiquesException("The parameter type is missing !")

        def parameter =  ["name": name,
                "humanName": json?."name" ?: "@id",
                "type": type,
                "valueKey": json?."value-key" ?: "[@ID]",
                "commandLineFlag": json?."command-line-flag" ?: "",
                "description": json?."description",
                "required": !(json?."optional" ?: false),
                "defaultValue": (json?."default-value" != null) ? json?."default-value" : "",
                "setByServer": json?."set-by-server" ?: false,
                "serverParameter": json?."server-parameter"?: false,
                "minimum": json?."minimum" as String,
                "maximum": json?."maximum" as String,
                "equals": json?."equals" as String,
                "in": json?."values-choice" as String,
                "uri": json?."uri",
                "uriPrintAttribut": json?."uri-print-attribute",
                "uriSortAttribut": json?."uri-sort-attribute"]

        return parameter.collectEntries {
            [(it.key): (it.value as String)?.replaceAll("@ID", name.toUpperCase())?.replaceAll("@id", name)]
        }
    }

}
