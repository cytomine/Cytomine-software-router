package be.cytomine.software.repository

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

import be.cytomine.client.CytomineException
import be.cytomine.client.collections.ParameterConstraintCollection
import be.cytomine.client.models.ParameterConstraint
import be.cytomine.client.models.Software
import be.cytomine.software.boutiques.Interpreter
import be.cytomine.software.consumer.Main
import be.cytomine.software.exceptions.BoutiquesException
import be.cytomine.software.repository.threads.ImagePullerThread
import groovy.util.logging.Log4j
import org.kohsuke.github.GHFileNotFoundException

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Log4j
class SoftwareManager {

    def prefixes = [:]
    def softwareTable = [:]
    def name

    GitHubManager gitHubManager
    DockerHubManager dockerHubManager

    SoftwareManager(def gitHubUsername, def dockerUsername, def prefix, def idSoftwareUserRepository) throws ClassNotFoundException {
        gitHubManager = new GitHubManager(gitHubUsername as String)
        dockerHubManager = new DockerHubManager(username: dockerUsername as String)
        prefixes << [(prefix): idSoftwareUserRepository]
        name =  "SoftwareManager $gitHubUsername / $dockerUsername"
    }

    def updateSoftware() {
        log.info("Refresh repository manager ${name} with prefixes: ${prefixes.keySet()}")
        def repositories = dockerHubManager.getRepositories()
        log.info(repositories)
        repositories.each { repository ->
            if (startsWithKnownPrefix(repository as String)) {
                log.info("Repository : ${repository}")
                Software currentSoftware = softwareTable.get((repository as String).trim().toLowerCase()) as Software
                def tags = dockerHubManager.getTags(repository as String)

                if (currentSoftware != null) {
                    if (currentSoftware.get("softwareVersion") as String != tags.first() as String) {
                        log.info("Update the software [${repository}]")

                        try {
                            def result = installSoftware(repository, tags.first())
                            Main.cytomine.deprecateSoftware(currentSoftware.getId())
                            softwareTable.put((repository as String).trim().toLowerCase(), result)

                            def imagePullerThread = new ImagePullerThread(pullingCommand: result.getStr("pullingCommand") as String)
                            ExecutorService executorService = Executors.newSingleThreadExecutor()
                            executorService.execute(imagePullerThread)
                        } catch (GHFileNotFoundException ex) {
                            log.info("Error during the installation of [${repository}] : ${ex.getMessage()}")
                        } catch (BoutiquesException ex) {
                            log.info("Boutiques exception : ${ex.getMessage()}")
                        } catch (CytomineException ex) {
                            log.info("Error during the adding of [${repository}] to Cytomine : ${ex.getMessage()}")
                        } catch (Exception ex) {
                            log.info("Unknown exception occurred : ${ex.getMessage()}")
                        }
                    }
                } else {
                    log.info("Add the software [${repository}]")

                    try {
                        def result = installSoftware(repository, tags.first())
                        softwareTable.put((repository as String).trim().toLowerCase(), result)

                        def imagePullerThread = new ImagePullerThread(pullingCommand: result.getStr("pullingCommand") as String)
                        ExecutorService executorService = Executors.newSingleThreadExecutor()
                        executorService.execute(imagePullerThread)
                    } catch (GHFileNotFoundException ex) {
                        log.info("Error during the installation of [${repository}] : ${ex.getMessage()}")
                    } catch (BoutiquesException ex) {
                        log.info("Boutiques exception : ${ex.getMessage()}")
                    } catch (CytomineException ex) {
                        log.info("Error during the adding of [${repository}] to Cytomine : ${ex.getMessage()}")
                    } catch (Exception ex) {
                        log.info("Unknown exception occurred : ${ex.printStackTrace()}")
                    }
                }
            }
        }
    }

    private def startsWithKnownPrefix(def repository) {
        return prefixes.find { prefix ->
            (repository as String).trim().toLowerCase().startsWith((prefix.key as String).trim().toLowerCase())
        }
    }

    private def installSoftware(def repository, def release) {
        def filename = gitHubManager.retrieveDescriptor(repository as String, release as String)

        Interpreter interpreter = new Interpreter(filename)
        def pullingInformation = interpreter.getPullingInformation()
        def imageName = interpreter.getImageName() + "-" + release as String

        def pullingCommand = 'singularity pull --name ' + imageName + '.simg docker://' +
                pullingInformation['image'] + ':' + release as String

        def software = interpreter.parseSoftware()
        def command = interpreter.buildExecutionCommand(imageName + ".simg")
        def arguments = interpreter.parseParameters()

        new File(filename).delete()

        def idSoftwareUserRepository = startsWithKnownPrefix(repository).value

        return addSoftwareToCytomine(release as String, software, command, arguments, pullingCommand,
                idSoftwareUserRepository)
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
    private def addSoftwareToCytomine(def version, def software, def command, def arguments, def pullingCommand,
                                      def idSoftwareUserRepository) throws CytomineException {
        // Add the piece of software
        def resultSoftware = Main.cytomine.addSoftware(
                version as String,
                software.name as String,
                idSoftwareUserRepository,
                software.processingServerId as Long,
                "",
                command as String,
                pullingCommand as String)

        if (software.description?.trim()) {
            Main.cytomine.addDescription(resultSoftware.getId(), "be.cytomine.processing.Software",
                    software.description as String)
        }

        // Load constraints
        ParameterConstraintCollection constraints = Main.cytomine.getParameterConstraints()

        // Add the arguments
        arguments.each { element ->
            def type = (element.type as String).toLowerCase().capitalize()
            if (type == 'Listdomain') type = 'ListDomain'
            def resultSoftwareParameter = Main.cytomine.addSoftwareParameter(
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
                    element.commandLineFlag as String)

            log.info(element)

            // Add the description
            if (element.description?.trim()) {
                Main.cytomine.addDescription(resultSoftwareParameter.getId(), "be.cytomine.processing.SoftwareParameter",
                        element.description as String)
            }

            // Add the constraints
            if (element.integer != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "integer") {
                        Main.cytomine.addSoftwareParameterConstraint(constraint.getId(),
                                resultSoftwareParameter.getId(), element.integer as String)
                    }
                }
            }

            if (element.minimum != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "minimum" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        Main.cytomine.addSoftwareParameterConstraint(constraint.getId(),
                                resultSoftwareParameter.getId(), element.minimum as String)
                    }
                }
            }
            if (element.maximum != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "maximum" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        Main.cytomine.addSoftwareParameterConstraint(constraint.getId(),
                                resultSoftwareParameter.getId(), element.maximum as String)
                    }
                }
            }
            if (element.equals != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "equals" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        Main.cytomine.addSoftwareParameterConstraint(constraint.getId(),
                                resultSoftwareParameter.getId(), element.equals as String)
                    }
                }
            }
            if (element.in != null) {
                for (int i = 0; i < constraints.size(); i++) {
                    ParameterConstraint constraint = constraints.get(i)
                    if (constraint.getStr("name").trim().toLowerCase() == "in" &&
                            constraint.getStr("dataType").trim().toLowerCase() == element.type.trim().toLowerCase()) {
                        Main.cytomine.addSoftwareParameterConstraint(constraint.getId(),
                                resultSoftwareParameter.getId(), element.in as String)
                    }
                }
            }
        }

        return resultSoftware
    }

}
