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
        this.processingServer = processingServer

        try {
            processingMethod = AbstractProcessingMethod.newInstance(this.processingServer.getStr("processingMethodName"))
            processingMethod.communication = new SSH(
                    host: processingServer.getStr("host"),
                    port: processingServer.getStr("port") as Integer,
                    user: processingServer.getStr("username"),
                    keyFilePath: Main.configFile.keyFilePath
            )

            log.info("HOST : ${processingServer.getStr("host")}")
            log.info("PORT : ${processingServer.getStr("port")}")
            log.info("USER : ${processingServer.getStr("username")}")

        } catch (ClassNotFoundException ex) {
            log.info(ex.toString())
        }
    }

    @Override
    void run() {
        JsonSlurper jsonSlurper = new JsonSlurper()

        log.info(mapMessage)

        QueueingConsumer consumer = new QueueingConsumer(channel)
        channel.basicConsume(mapMessage["name"] as String, true, consumer)

        while (true) {
            log.info("ProcessingServerThread waiting on queue : " + mapMessage["name"])

            QueueingConsumer.Delivery delivery = consumer.nextDelivery()
            String message = new String(delivery.getBody())

            def mapMessage = jsonSlurper.parseText(message)

            switch (mapMessage["requestType"]) {
                case "execute":
                    log.info("Try pulling the image")

                    def pullingCommand = mapMessage["pullingCommand"] as String
                    def temp = pullingCommand.substring(pullingCommand.indexOf("--name ") + "--name ".size(), pullingCommand.size())
                    def imageName = temp.substring(0, temp.indexOf(" "))

                    def imageExists = new File("./${imageName}").exists()

                    def pullingResult = 0
                    if (!imageExists) {
                        def process = pullingCommand.execute()
                        process.waitFor()
                        pullingResult = process.exitValue()
                    }

                    if (imageExists || pullingResult == 0) {
                        Long jobId = mapMessage["jobId"] as Long

                        String command = ""
                        mapMessage["command"].each { command += it.toString() + " " }

                        log.info("Execute command : ${command}")

                        Runnable jobExecutionThread = new JobExecutionThread(
                                processingMethod: processingMethod,
                                command: command,
                                cytomineJobId: jobId,
                                runningJobs: runningJobs,
                                serverParameters: mapMessage["serverParameters"]
                        )
                        synchronized (runningJobs) {
                            runningJobs.put(jobId, jobExecutionThread)
                        }
                        ExecutorService executorService = Executors.newSingleThreadExecutor()
                        executorService.execute(jobExecutionThread)

                        Main.cytomine.changeStatus(jobId, Cytomine.JobStatus.INQUEUE, 0)
                    } else {
                        log.info("A problem occurred during the pulling process !")
                    }

                    break
                case "kill":
                    log.info("kill")

                    int jobId = mapMessage["jobId"]

                    log.info(jobId)

                    synchronized (runningJobs) {
                        runningJobs.each { id, thread -> println id}
                        if (runningJobs.containsKey(jobId)) {
                            (runningJobs.get(jobId) as JobExecutionThread).kill()
                            runningJobs.remove(jobId)
                        }
                    }

                    break
            }
        }
    }

}
