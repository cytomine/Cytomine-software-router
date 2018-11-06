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

import be.cytomine.client.Cytomine
import be.cytomine.client.models.ProcessingServer
import be.cytomine.software.communication.SSH
import be.cytomine.software.consumer.Main
import be.cytomine.software.processingmethod.AbstractProcessingMethod
import com.rabbitmq.client.Channel
import com.rabbitmq.client.QueueingConsumer
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Log4j
class ProcessingServerThread implements Runnable {

    private Channel channel
    private AbstractProcessingMethod processingMethod
    private ProcessingServer processingServer
    private def mapMessage
    def runningJobs = [:]

    ProcessingServerThread(Channel channel, def mapMessage, ProcessingServer processingServer) {
        this.channel = channel
        this.mapMessage = mapMessage
        updateProcessingServer(processingServer)
    }

    def updateProcessingServer(def newProcessingServer) {
        this.processingServer = newProcessingServer

        try {
            processingMethod = AbstractProcessingMethod.newInstance(this.processingServer.getStr("processingMethodName"))
            processingMethod.communication = new SSH(
                    host: processingServer.getStr("host"),
                    port: processingServer.getStr("port") as Integer,
                    user: processingServer.getStr("username"),
                    keyFilePath: Main.configFile.cytomine.software.sshKeysFile
            )

            log.info("Processing server : ${processingServer.getStr("name")}")
            log.info("================================================")
            log.info("host : ${processingServer.getStr("host")}")
            log.info("port : ${processingServer.getStr("port")}")
            log.info("user : ${processingServer.getStr("username")}")
            log.info("================================================")

        } catch (ClassNotFoundException ex) {
            log.info(ex.toString())
        }
    }

    @Override
    void run() {
        JsonSlurper jsonSlurper = new JsonSlurper()

        QueueingConsumer consumer = new QueueingConsumer(channel)
        channel.basicConsume(mapMessage["name"] as String, true, consumer)

        while (true) {
            def logPrefix = "[${processingServer.getStr("name")}]"
            log.info("${logPrefix} Thread waiting on queue : ${mapMessage["name"]}")

            QueueingConsumer.Delivery delivery = consumer.nextDelivery()
            String message = new String(delivery.getBody())

            def mapMessage = jsonSlurper.parseText(message)
            log.info("${logPrefix} Received message: ${mapMessage}")

            try {
                switch (mapMessage["requestType"]) {
                    case "execute":
                        Long jobId = mapMessage["jobId"] as Long
                        logPrefix += "[Job ${jobId}]"
                        log.info("${logPrefix} Try to execute... ")

                        log.info("${logPrefix} Try to find image... ")
                        def pullingCommand = mapMessage["pullingCommand"] as String
                        def temp = pullingCommand.substring(pullingCommand.indexOf("--name ") + "--name ".size(), pullingCommand.size())
                        def imageName = temp.substring(0, temp.indexOf(" "))

                        try {
                            Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.WAIT, 0, "Try to find image [${imageName}]")
                        } catch (Exception e) {}
                        synchronized (Main.pendingPullingTable) {
                            def start = System.currentTimeSeconds()
                            while (Main.pendingPullingTable.contains(imageName)) {
                                def status = "The image [${imageName}] is currently being pulled ! Wait..."
                                log.warn("${logPrefix} ${status}")
                                try {
                                    Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.WAIT, 0, status)
                                } catch (Exception e) {}

                                if (System.currentTimeSeconds() - start > 1800) {
                                    status = "A problem occurred during the pulling process !"
                                    Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.FAILED, 0, status)
                                    return
                                }

                                sleep(60000)
                            }
                        }

                        def imageExists = new File("${Main.configFile.cytomine.software.path.softwareImages}/${imageName}").exists()

                        def pullingResult = 0
                        if (!imageExists) {
                            log.info("${logPrefix} Image not found locally ")
                            log.info("${logPrefix} Try pulling image... ")
                            def process = pullingCommand.execute()
                            process.waitFor()
                            pullingResult = process.exitValue()

                            if (pullingResult == 0) {
                                def movingProcess = ("mv ${imageName} ${Main.configFile.cytomine.software.path.softwareImages}").execute()
                                movingProcess.waitFor()
                            }
                        }

                        if (imageExists || pullingResult == 0) {
                            log.info("${logPrefix} Found image!")

                            String command = ""
                            mapMessage["command"].each {
                                if (command == "singularity run ") {
                                    command += processingServer.getStr("persistentDirectory")
                                    command += (processingServer.getStr("persistentDirectory") ? File.separator : "")
                                }
                                command += it.toString() + " "
                            }

                            log.info("${logPrefix} Job in queue!")
                            Runnable jobExecutionThread = new JobExecutionThread(
                                    processingMethod: processingMethod,
                                    command: command,
                                    cytomineJobId: jobId,
                                    runningJobs: runningJobs,
                                    serverParameters: mapMessage["serverParameters"],
                                    persistentDirectory: processingServer.getStr("persistentDirectory"),
                                    workingDirectory: processingServer.getStr("workingDirectory")
                            )
                            synchronized (runningJobs) {
                                runningJobs.put(jobId, jobExecutionThread)
                            }
                            Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.INQUEUE, 0)
                            ExecutorService executorService = Executors.newSingleThreadExecutor()
                            executorService.execute(jobExecutionThread)
                        } else {
                            def status = "A problem occurred during the pulling process !"
                            log.error("${logPrefix} ${status}")
                            Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.FAILED, 0, status)
                        }

                        break
                    case "kill":
                        def jobId = mapMessage["jobId"] as Long

                        log.info("${logPrefix} Try killing the job : ${jobId}")

                        synchronized (runningJobs) {
                            if (runningJobs.containsKey(jobId)) {
                                (runningJobs.get(jobId) as JobExecutionThread).kill()
                                runningJobs.remove(jobId)
                            }
                            else {
                                Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.KILLED, 0)
                            }
                        }

                        break
                    case "updateProcessingServer":
                        ProcessingServer processingServer = Main.cytomine.getProcessingServer(mapMessage["processingServerId"] as Long)
                        updateProcessingServer(processingServer)

                        break
                }
            }
            catch (Exception e) {
                log.info(e.printStackTrace())
            }
        }
    }

}
