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

import groovy.util.logging.Log4j

@Log4j
class SlurmProcessingMethod extends AbstractProcessingMethod {

    protected def defaultTime = '10:00'

    @Override
    def executeJob(def command, def serverParameters) {
        // Build the slurm arguments
        def slurmCommand = 'sbatch --output=%A.out --time=' + defaultTime

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

        // Move the image from the local machine to the server
        def imageExistsOnServer = communication.executeCommand("(ls ${imageName} && echo \"true\") || echo \"false\"") as Boolean
        if (!imageExistsOnServer) {
            def importResult = communication.copyLocalToRemote("./", "./", imageName)
        }

        // Execute the command on the processing server
        def executionCommand = '''echo "#!/bin/bash
''' + command + '''"|''' + slurmCommand
        def response = communication.executeCommand(executionCommand)

        log.info("Command : ${executionCommand}")

        def jobId = (response =~ /(\d+)/)
        return jobId.find() ? jobId.group() as Integer : -1
    }

    @Override
    def isAlive(def jobId) {
        def aliveCommand = "squeue -j ${jobId}"
        def response = communication.executeCommand(aliveCommand)

        return (response =~ /(\d+)/).find()
    }

    @Override
    def retrieveLogs(def jobId, def outputFile) {
        return communication.copyRemoteToLocal(".", "./algo/logs/${outputFile}.out", "${jobId}.out")
    }

    @Override
    def killJob(def jobId) {
        def killCommand = "scancel ${jobId}"
        def result = communication.executeCommand(killCommand)
        return result == ""
    }

}
