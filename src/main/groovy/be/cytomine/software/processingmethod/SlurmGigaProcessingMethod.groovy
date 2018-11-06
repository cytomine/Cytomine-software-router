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
class SlurmGigaProcessingMethod extends SlurmProcessingMethod {

    @Override
    def executeJob(def command, def serverParameters, def workingDirectory) {
        // Build the slurm arguments
        def output = workingDirectory?:'' + (workingDirectory ? File.separator : '') + '%A.out'
        def slurmCommand = 'sbatch -p Public --output=' + output + ' --time=' + DEFAULT_TIME

        if (serverParameters != null) {
            slurmCommand = 'sbatch -p Public --output=' + output + ' '

            def timeSet = false
            serverParameters.each { element ->
                if (element.key == "time") timeSet = true
                slurmCommand += '--' + element.key + '=' + element.value +  ' '
            }
        }

        // Get the image name
        def temp = (command as String).replace("singularity run ", "").trim()
        def imageName = new File(temp.substring(0, temp.indexOf(" ")))

        log.info("Image name : ${imageName}")

        // Move the image from the local machine to the server
        def existCommand = "test -f ${imageName} && echo \"true\" || echo \"false\""

        def success = false
        def retryOnError = true
        def errorMessage = ""
        for (int i = 0; i < RETRY_ON_ERROR && retryOnError && !success; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                def imageExistsOnServer = Boolean.parseBoolean((communication.executeCommand(existCommand) as String).trim())
                if (!imageExistsOnServer) {
                    communication.copyLocalToRemote("${Main.configFile.cytomine.software.path.softwareImages}/",
                            "${imageName.getParent()}/", imageName.getName())
                }
                success = true
            } catch (JSchException ex) {
                errorMessage = ex.getMessage()
                log.info(errorMessage)
                retryOnError = true
            } catch (Exception ex) {
                errorMessage = ex.getMessage()
                log.info(errorMessage)
                retryOnError = false
            }
        }

        if (!success) return [jobId:-1, message:errorMessage]

        // Execute the command on the processing server
        def executionCommand = '''cd ''' + (workingDirectory?:".") + ''' && echo "#!/bin/bash
export PATH=$PATH:/home/mass/opt/gridbin/bin
''' + command + '''"|''' + slurmCommand

        log.info("Command : ${executionCommand}")

        retryOnError = true
        for (int i = 0; i < RETRY_ON_ERROR && retryOnError; i++) {
            log.info("Attempt : ${(i + 1)}")
            try {
                def response = communication.executeCommand(executionCommand) as String
                def responseWithoutColorCode = response.replaceAll('\u001B\\[[;\\d]*m', '')

                def jobId = (responseWithoutColorCode =~ /(\d+)/)
                return [jobId: jobId.find() ? jobId.group() as Integer : -1, message: ""]
            } catch (JSchException ex) {
                errorMessage = ex.getMessage()
                log.info(errorMessage)
                retryOnError = true
            } catch (Exception ex) {
                errorMessage = ex.getMessage()
                log.info(errorMessage)
                retryOnError = false
            }
        }

        return [jobId: -1, message: errorMessage]
    }

}
