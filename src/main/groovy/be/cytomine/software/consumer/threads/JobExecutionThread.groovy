package be.cytomine.software.consumer.threads

import be.cytomine.client.Cytomine

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
import be.cytomine.software.processingmethod.AbstractProcessingMethod
import groovy.util.logging.Log4j

@Log4j
class JobExecutionThread implements Runnable {

    AbstractProcessingMethod processingMethod
    def refreshRate = 15
    def command
    def cytomineJobId
    def serverJobId
    def runningJobs = [:]
    def serverParameters
    def persistentDirectory
    def workingDirectory
    
    def logPrefix() { 
        "[Job ${cytomineJobId}]" 
    }

    @Override
    void run() {
        try {
            // Executes a job on a server using a processing method(slurm,...) and a communication method (SSH,...)
            def result = processingMethod.executeJob(command, serverParameters, workingDirectory)
            serverJobId = result['jobId']
            if (serverJobId == -1) {
                log.error("${logPrefix()} Job failed! Reason: ${result['message']}")
                Main.cytomine.changeStatus(cytomineJobId, Cytomine.JobStatus.FAILED, 0, result['message'] as String)
                return
            }

            log.info("${logPrefix()} Job launched successfully !")
            log.info("${logPrefix()} Cytomine job id   : ${cytomineJobId}")
            log.info("${logPrefix()} Server job id     : ${serverJobId}")

            // Wait until the end of the job
            while (processingMethod.isAlive(serverJobId)) {
                log.info("${logPrefix()} Job is running !")

                sleep((refreshRate as Long) * 1000)
            }

            // Retrieve the slurm job log
            if (processingMethod.retrieveLogs(serverJobId, cytomineJobId, workingDirectory)) {
                log.info("${logPrefix()} Logs retrieved successfully !")

                def filePath = "${Main.configFile.cytomine.software.path.jobs}/log.out"
                def logFile = new File(filePath)

                if (logFile.exists()) {
                    // Upload the log file as an attachedFile to the Cytomine-core
                    Main.cytomine.uploadAttachedFile(filePath as String, "be.cytomine.processing.Job", cytomineJobId
                            as Long)

                    // Remove the log file
                    new File(filePath as String).delete()
                }
            } else {
                log.error("${logPrefix()} Logs not retrieved !")
            }
        }
        catch (Exception e) {
            // Indeterminate status because job could have been launched before the exception
            Main.cytomine.changeStatus(cytomineJobId, Cytomine.JobStatus.INDETERMINATE, 0, e.getMessage())
        }

        // Remove the job id from the running jobs
        notifyEnd()
    }

    void kill() {
        if (processingMethod.killJob(serverJobId)) {
            synchronized (runningJobs) {
                runningJobs.remove(cytomineJobId)
            }
            log.info("${logPrefix()} The job [${cytomineJobId}] has been killed successfully !")
            Main.cytomine.changeStatus(cytomineJobId, 8, 0) // Cytomine.JobStatus.KILLED = 8
        }
        else {
            log.info("${logPrefix()} The job [${cytomineJobId}] has not been killed !")
            Main.cytomine.changeStatus(cytomineJobId, Cytomine.JobStatus.INDETERMINATE, 0)
        }
    }

    synchronized void notifyEnd() {
        runningJobs.remove(cytomineJobId)
    }
}
