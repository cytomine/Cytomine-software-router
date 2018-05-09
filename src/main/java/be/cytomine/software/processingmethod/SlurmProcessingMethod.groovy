package be.cytomine.software.processingmethod

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

import be.cytomine.software.consumer.Main
import com.jcraft.jsch.JSchException
import groovy.util.logging.Log4j

@Log4j
class SlurmProcessingMethod extends AbstractProcessingMethod {

    protected final def DEFAULT_TIME = '10:00'

    @Override
    def executeJob(def command, def serverParameters) {
        // Build the slurm arguments
        def slurmCommand = 'sbatch --output=%A.out --time=' + DEFAULT_TIME

        if (serverParameters != null) {
            slurmCommand = 'sbatch --output=%A.out '

            def timeSet = false
            serverParameters.each { element ->
                if (element.key == "time") timeSet = true
                slurmCommand += '--' + element.key + '=' + element.value +  ' '
            }
        }

        // Get the image name
        def temp = (command as String).replace("singularity run ", "").trim()
        def imageName = temp.substring(0, temp.indexOf(" "))

        log.info("Image name : ${imageName}")

        // Move the image from the local machine to the server
        def existCommand = "test -f \$HOME/${imageName} && echo \"true\" || echo \"false\""

        def success = false
        def retryOnError = true
        for (int i = 0; i < RETRY_ON_ERROR && retryOnError && !success; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                def imageExistsOnServer = Boolean.parseBoolean((communication.executeCommand(existCommand) as String).trim())
                if (!imageExistsOnServer) {
                    communication.copyLocalToRemote("./${Main.configFile.imagesDirectory}/", "./", imageName)
                }
                success = true
            } catch (JSchException ex) {
                log.info(ex.getMessage())
                retryOnError = true
            } catch (Exception ex) {
                log.info(ex.getMessage())
                retryOnError = false
            }
        }

        if (!success) return -1

        // Execute the command on the processing server
        def executionCommand = '''echo "#!/bin/bash
''' + command + '''"|''' + slurmCommand

        log.info("Command : ${executionCommand}")

        retryOnError = true
        for (int i = 0; i < RETRY_ON_ERROR && retryOnError; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                def response = communication.executeCommand(executionCommand) as String
                def responseWithoutColorCode = response.replaceAll('\u001B\\[[;\\d]*m', '')

                def jobId = (responseWithoutColorCode =~ /(\d+)/)
                return jobId.find() ? jobId.group() as Integer : -1
            } catch (JSchException ex) {
                log.info(ex.getMessage())
                retryOnError = true
            } catch (Exception ex) {
                log.info(ex.getMessage())
                retryOnError = false
            }
        }

        return -1
    }

    @Override
    def isAlive(def jobId) {
        def aliveCommand = "squeue -j ${jobId}"
        def response = communication.executeCommand(aliveCommand) as String
        def responseWithoutColorCode = response.replaceAll('\u001B\\[[;\\d]*m', '')

        return (responseWithoutColorCode =~ /(\d+)/).find()
    }

    @Override
    def retrieveLogs(def jobId, def outputFile) {
        def retryOnError = true

        for (int i = 0; i < RETRY_ON_ERROR && retryOnError; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                communication.copyRemoteToLocal(".", "${Main.configFile.logsDirectory}/${outputFile}.out", "${jobId}.out")
                return true
            } catch (JSchException ex) {
                log.info(ex.getMessage())
                retryOnError = true
            } catch (Exception ex) {
                log.info(ex.getMessage())
                retryOnError = false
            }
        }

        return false
    }

    @Override
    def killJob(def jobId) {
        def killCommand = "scancel ${jobId}"

        def retryOnError = true
        for (int i = 0; i < RETRY_ON_ERROR && retryOnError; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                def result = communication.executeCommand(killCommand)
                return result == ""
            } catch (JSchException ex) {
                log.info(ex.getMessage())
                retryOnError = true
            } catch (Exception ex) {
                log.info(ex.getMessage())
                retryOnError = false
            }
        }

        return false
    }

}
