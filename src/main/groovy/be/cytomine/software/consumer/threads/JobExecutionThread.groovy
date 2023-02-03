package be.cytomine.software.consumer.threads

import be.cytomine.client.Cytomine

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

import be.cytomine.software.consumer.Main
import be.cytomine.software.processingmethod.AbstractProcessingMethod
import be.cytomine.software.repository.threads.ImagePullerThread
import groovy.util.logging.Log4j2
import be.cytomine.client.CytomineException
import be.cytomine.client.models.Job
import be.cytomine.client.models.AttachedFile

@Log4j2
class JobExecutionThread implements Runnable {
    private final DEFAULT_LOG_REFRESH_RATE = 15
    private final int DEFAULT_PULLING_REFRESH_RATE = 20
    private final int DEFAULT_PULLING_TIMEOUT = 1800

    AbstractProcessingMethod processingMethod
    def refreshRate = (Main.configFile.cytomine.software.job.logRefreshRate as int) ?: DEFAULT_LOG_REFRESH_RATE
    int pullingRefreshRate = (Main.configFile.cytomine.software.pullingCheckRefreshRate as int) ?: DEFAULT_PULLING_REFRESH_RATE
    int pullingTimeout = (Main.configFile.cytomine.software.pullingCheckTimeout as int) ?: DEFAULT_PULLING_TIMEOUT
    def pullingCommand
    def runCommand
    def cytomineJobId
    def serverJobId
    def runningJobs = [:]
    def serverParameters
    def persistentDirectory
    def workingDirectory

    def logPrefix() {
        "[Job ${cytomineJobId}]"
    }

    def getAdaptedRunCommand() {
        String cmd = ""
        runCommand.each {
            if (cmd == "singularity run ") {
                cmd += persistentDirectory
                cmd += ((persistentDirectory) ? File.separator : "")
            }
            cmd += it.toString() + " "
        }
        return cmd
    }

    def getImageName() {
        def temp = pullingCommand.substring(pullingCommand.indexOf("--name ") + "--name ".size(), pullingCommand.size())
        return temp.substring(0, temp.indexOf(" "))
    }

    @Override
    void run() {
        try {
            log.info("${logPrefix()} Try to execute... ")

            log.info("${logPrefix()} Try to find image... ")
            def imageName = getImageName()
            // 1) The image is being pulled.
            def wasPulling = false
            def start = System.currentTimeSeconds()
            while (Main.pendingPullingTable.contains(imageName)) {
                wasPulling = true
                def status = "The image [${imageName}] is currently being pulled ! Wait..."
                log.warn("${logPrefix()} ${status}")
                try {
                    Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.WAIT, 0, status)
                } catch (Exception ignored) {}

                if (System.currentTimeSeconds() - start > pullingTimeout) {
                    status = "A problem occurred during the pulling process !"
                    Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.FAILED, 0, status)
                    notifyEnd()
                    return
                }

                sleep(pullingRefreshRate * 1000)
            }

            def imageExists = new File("${Main.configFile.cytomine.software.path.softwareImages}/${imageName}").exists()
            def status
            if (!imageExists) {
                // 2) if image was being pulled but not exist at the end: error
                if (wasPulling) {
                    status = "A problem occurred during the pulling process !"
                    log.error("${logPrefix()} ${status}")
                    Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.FAILED, 0, status)
                    notifyEnd()
                    return
                }

                // 3) image is not pulled and was not being pulled just before
                log.info("${logPrefix()} Image not found locally ")
                def imagePuller = new ImagePullerThread(pullingCommand: pullingCommand)

                status = "The image [${imageName}] is currently being pulled ! Wait..."
                log.warn("${logPrefix()} ${status}")
                try {
                    Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.WAIT, 0, status)
                } catch (Exception ignored) {}

                imagePuller.run()
                imageExists = new File("${Main.configFile.cytomine.software.path.softwareImages}/${imageName}").exists()
                if (!imageExists) {
                    status = "A problem occurred during the pulling process !"
                    log.error("${logPrefix()} ${status}")
                    Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.FAILED, 0, status)
                    notifyEnd()
                    return
                }
            }

            log.info("${logPrefix()} Found image!")
            def runCommand = getAdaptedRunCommand()
            log.info("${logPrefix()} ${runCommand}")

            // Executes a job on a server using a processing method(slurm,...) and a communication method (SSH,...)
            def result = processingMethod.executeJob(runCommand, serverParameters, persistentDirectory, workingDirectory)
            try {
                Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.INQUEUE, 0)
            } catch (CytomineException ignored) {}

            serverJobId = result['jobId']
            if (serverJobId == -1) {
                log.error("${logPrefix()} Job failed! Reason: ${result['message']}")
                Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.FAILED, 0, result['message'] as String)
                notifyEnd()
                return
            }

            log.info("${logPrefix()} Job launched successfully !")
            log.info("${logPrefix()} Cytomine job id   : ${cytomineJobId}")
            log.info("${logPrefix()} Server job id     : ${serverJobId}")

            // Wait until the end of the job
            while (processingMethod.isAlive(serverJobId)) {
                log.info("${logPrefix()} Job is running !")

                sleep(refreshRate * 1000)
            }

            Job job = new Job().fetch(cytomineJobId)
            def notStartedStatuses = [Job.JobStatus.INQUEUE, Job.JobStatus.NOTLAUNCH, Job.JobStatus.WAIT]
            if (notStartedStatuses.contains(job.getInt('status'))) {
                Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.FAILED, 0)
            }

            // Retrieve the slurm job log
            if (processingMethod.retrieveLogs(serverJobId, cytomineJobId, workingDirectory)) {
                log.info("${logPrefix()} Logs retrieved successfully !")

                def filePath = "${Main.configFile.cytomine.software.path.jobs}/log.out"
                def logFile = new File(filePath)

                if (logFile.exists()) {
                    // Upload the log file as an attachedFile to the Cytomine-core
                    new AttachedFile("be.cytomine.processing.Job", cytomineJobId as Long, filePath as String).save()


                    // Remove the log file
                    new File(filePath as String).delete()
                }
            } else {
                log.error("${logPrefix()} Logs not retrieved !")
            }
        }
        catch (Exception e) {
            log.error e
            // Indeterminate status because job could have been launched before the exception
            Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.INDETERMINATE, 0, e.getMessage().take(255).toString())
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
            Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.KILLED, 0) // Job.JobStatus.KILLED = 8
        }
        else {
            log.error("${logPrefix()} The job [${cytomineJobId}] has not been killed !")
            Cytomine.instance.changeStatus(cytomineJobId, Job.JobStatus.INDETERMINATE, 0)
        }
    }

    synchronized void notifyEnd() {
        runningJobs.remove(cytomineJobId)
    }
}
