package be.cytomine.software.consumer.threads

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

    @Override
    void run() { // TODO: something's no working here
        // Executes a job on a server using a processing method(slurm,...) and a communication method (SSH,...)
        serverJobId = processingMethod.executeJob(command, serverParameters)

        if (serverJobId == -1) {
            log.info("Job ${cytomineJobId} failed !")
            return
        }

        log.info("Job ${cytomineJobId} launched successfully !")

        // Wait until the job has ended
        while (processingMethod.isAlive(serverJobId)) {
            log.info("${serverJobId} is running !")

            sleep((refreshRate as Long) * 1000)
        }

        // Retrieve the slurm job log
        if (processingMethod.retrieveLogs(serverJobId, cytomineJobId)) {
            log.info("Logs retrieved successfully !")

            def filePath = "./algo/logs/${cytomineJobId}.out"
            def logFile = new File(filePath)

            if (logFile.exists()) {
                // Upload the lof file as an attachedFile to the Cytomine-Core
                Main.cytomine.uploadAttachedFile(filePath as String, "Job", cytomineJobId as Long)

                // Remove the log file
                new File(filePath as String).delete()
            }

            // Remove the job id from the running jobs
            notifyEnd()
        } else {
            log.info("Logs not retrieved !")
        }

    }

    void kill() {
        log.info("Try killing job")

        if (processingMethod.killJob(serverJobId)) {
            synchronized (runningJobs) {
                runningJobs.remove(cytomineJobId)
            }
            log.info("The job has been successfully killed !")
        }
        else log.info("The job has not been killed !")
    }

    synchronized void notifyEnd() {
        runningJobs.remove(cytomineJobId)
    }

}
