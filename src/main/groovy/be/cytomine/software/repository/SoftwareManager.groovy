package be.cytomine.software.repository

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
import be.cytomine.client.models.*
import be.cytomine.software.exceptions.BoutiquesException
import be.cytomine.software.management.GitHubSoftwareManager
import be.cytomine.software.repository.threads.ImagePullerThread
import be.cytomine.client.CytomineException

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
                Software currentSoftware = softwareTable.get((repository as String).trim().toLowerCase()) as Software
                log.info("Repository to refresh : ${repository} - Last version installed is ${currentSoftware?.get("softwareVersion")}")
                def tags = dockerHubManager.getTags(repository as String)
                log.info("tags : $tags")
                if (tags.isEmpty()) {
                    log.info "Tags not found for Docker image $repository. Software not added"
                    return
                }

                //if there is no version of this software or we don't have this version, we add it
                if (currentSoftware == null || (currentSoftware.get("softwareVersion") as String != tags.first() as String)) {

                    if (currentSoftware == null) log.info("-> Add the software [${repository}] - ${tags.first()}")
                    else log.info("-> Update the software [${repository}] - ${tags.first()}")

                    try {
                        GitHubSoftwareManager softManager = new GitHubSoftwareManager(gitHubManager, (repository as String).trim().toLowerCase(),
                                tags.first(), startsWithKnownPrefix(repository).value)
                        def result = softManager.installSoftware()

                        if (currentSoftware != null) currentSoftware.deprecate()

                        softwareTable.put((repository as String).trim().toLowerCase(), result)

                        def imagePullerThread = new ImagePullerThread(pullingCommand: result.getStr("pullingCommand") as String)
                        ExecutorService executorService = Executors.newSingleThreadExecutor()
                        executorService.execute(imagePullerThread)
                    } catch (GHFileNotFoundException ex) {
                        log.info("--> Error during the installation of [${repository}] : ${ex.getMessage()}")
                    } catch (BoutiquesException ex) {
                        log.info("--> Boutiques exception : ${ex.getMessage()}")
                    } catch (CytomineException ex) {
                        log.info("--> Error during the adding of [${repository}] to Cytomine : ${ex.getMessage()} ${ex.getHttpCode()} ${ex.getMsg()}")
                    } catch (Exception ex) {
                        log.info("--> Unknown exception occurred : ${ex.printStackTrace()}")
                    }

                } else {
                    log.info "-> Last version is already installed. Skip."
                }

            }
        }

        log.info "Finished to refresh."
    }

    private def startsWithKnownPrefix(def repository) {
        return prefixes.find { prefix ->
            (repository as String).trim().toLowerCase().startsWith((prefix.key as String).trim().toLowerCase())
        }
    }

}
