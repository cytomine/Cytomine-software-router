package be.cytomine.software.management

/*
 * Copyright (c) 2009-2020. Authors: see NOTICE file.
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

import be.cytomine.client.CytomineException
import be.cytomine.client.collections.Collection
import be.cytomine.client.models.*
import be.cytomine.software.boutiques.Interpreter
import groovy.util.logging.Log4j

@Log4j
abstract class AbstractSoftwareManager {

    String release
    Long idSoftwareUserRepository
    Long softwareId


    abstract protected File retrieveDescriptor()
    abstract protected String generateSingularityBuildingCommand(Interpreter interpreter)

    public Software installSoftware() {
        def descriptor = this.retrieveDescriptor()

        Interpreter interpreter = new Interpreter(descriptor.path)
        checkDescriptor(interpreter)
        def pullingCommand = generateSingularityBuildingCommand(interpreter)
        def software = interpreter.parseSoftware()

        def imageName = interpreter.getImageName() + "-" + release as String


        def command = interpreter.buildExecutionCommand(imageName + ".simg")
        def arguments = interpreter.parseParameters()

        return addSoftwareToCytomine(software, command, arguments, pullingCommand)
    }

    abstract protected void checkDescriptor(Interpreter interpreter);
    abstract void cleanFiles();

    void cleanFiles(File... files){
        files.each {
            if(it.isDirectory()) it.deleteDir()
            else it.delete()
        }
    }

    /**
     * The method will be used to actually add a piece of software and its parameters to the Cytomine-core interface.
     * The eventual constraints associated to a software parameter will be added as well.
     * @param version : the GitHub tag used as an image version by Docker Hub
     * @param software : the information like the name and the default processingServer of a piece of software
     * @param command : the command used to run the Docker container
     * @param arguments : the arguments of the upon command
     * @param pullingCommand : the command used to pull and convert a Docker image to a Singularity image
     * @return the newly added piece of software
     * @throws CytomineException : exceptions related to the Cytomine java client
     */
    private Software addSoftwareToCytomine(def software, def command, def arguments, def pullingCommand) throws CytomineException {
        // Add the piece of software
        log.info("Add the piece of software")
        def resultSoftware = new Software(
                software.name as String,
                "",
                command as String,
                release,
                idSoftwareUserRepository,
                software.processingServerId as Long,
                pullingCommand as String)


        if(this instanceof GitHubSoftwareManager) resultSoftware.set("sourcePath", this.getSourcePath())

        if(softwareId == null) {
            resultSoftware = resultSoftware.save()
        } else {
            //When we only had a software with the sourcePath, we finish its installation into the DB with an update
            resultSoftware.set("id", softwareId)
            resultSoftware = resultSoftware.update()
        }

        if (software.description?.trim()) {
            new Description("Software",resultSoftware.getId(), software.description as String).save()
        }

        // Load constraints
        log.info("Load constraints")
        Collection<ParameterConstraint> constraints = Collection.fetch(ParameterConstraint.class)

        // Add the arguments
        log.info("Add the arguments")
        arguments.each { element ->
            log.info(element)

            String type = (element.type as String).toLowerCase().capitalize()
            if(type == "Listdomain") type = "ListDomain"
            def resultSoftwareParameter = new SoftwareParameter(
                    element.name as String,
                    type,
                    resultSoftware.getId(),
                    element.defaultValue as String,
                    element.required.toBoolean(),
                    element.index as Integer,
                    element.uri as String,
                    element.uriSortAttribut as String,
                    element.uriPrintAttribut as String,
                    element.setByServer.toBoolean(),
                    element.serverParameter.toBoolean(),
                    element.humanName as String,
                    element.valueKey as String,
                    element.commandLineFlag as String).save()
            assert resultSoftwareParameter.getId() != null

            // Add the description
            log.info("Add the argument description")
            if (element.description?.trim()) {
                new Description("SoftwareParameter",resultSoftwareParameter.getId(), element.description as String).save()
            }

            // Add the constraints
            log.info("Add the argument constraints")
            if (element.integer != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "integer") {
                        new SoftwareParameterConstraint(constraint.getId(), resultSoftwareParameter.getId(), element.integer as String).save()
                    }
                }
            }

            if (element.minimum != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "minimum" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        new SoftwareParameterConstraint(constraint.getId(), resultSoftwareParameter.getId(), element.minimum as String).save()
                    }
                }
            }
            if (element.maximum != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "maximum" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        new SoftwareParameterConstraint(constraint.getId(), resultSoftwareParameter.getId(), element.maximum as String).save()
                    }
                }
            }
            if (element.equals != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "equals" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        new SoftwareParameterConstraint(constraint.getId(), resultSoftwareParameter.getId(), element.equals as String).save()
                    }
                }
            }
            if (element.in != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "in" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        new SoftwareParameterConstraint(constraint.getId(), resultSoftwareParameter.getId(), element.in as String).save()
                    }
                }
            }
        }

        return resultSoftware
    }

    abstract protected String getSourcePath()

}
