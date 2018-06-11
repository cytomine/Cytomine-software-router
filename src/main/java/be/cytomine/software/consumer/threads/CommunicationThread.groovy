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

import be.cytomine.client.models.ProcessingServer
import be.cytomine.software.consumer.Main
import be.cytomine.software.repository.AbstractRepositoryManager
import be.cytomine.software.repository.SoftwareManager
import be.cytomine.software.repository.threads.RepositoryManagerThread

import com.rabbitmq.client.Channel
import com.rabbitmq.client.QueueingConsumer
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Log4j
class CommunicationThread implements Runnable {

    RepositoryManagerThread repositoryManagerThread

    Channel channel
    String queueName
    String exchangeName

    @Override
    void run() {
        channel.exchangeDeclare(exchangeName, "direct", true)
        channel.queueDeclare(queueName, true, false, false, null)
        channel.queueBind(queueName, exchangeName, "")

        QueueingConsumer consumer = new QueueingConsumer(channel)
        channel.basicConsume(queueName, true, consumer)

        JsonSlurper jsonSlurper = new JsonSlurper()

        while (true) {
            log.info("Communication thread waiting on queue : " + queueName)

            // Waiting for a new message
            QueueingConsumer.Delivery delivery = consumer.nextDelivery()
            String message = new String(delivery.getBody())

            def mapMessage = jsonSlurper.parseText(message)
            switch (mapMessage["requestType"]) {
                case "addProcessingServer":
                    log.info("Add a new processing server : " + mapMessage["name"])

                    ProcessingServer processingServer = Main.cytomine.getProcessingServer(mapMessage["processingServerId"] as Long)

                    // Launch the processingServerThread associated to the upon processingServer
                    Runnable processingServerThread = new ProcessingServerThread(channel, mapMessage, processingServer)
                    ExecutorService executorService = Executors.newSingleThreadExecutor()
                    executorService.execute(processingServerThread)
                    break
                case "addSoftwareUserRepository":
                    log.info("Add a new software user repository")
                    log.info("============================================")
                    log.info("username          : ${mapMessage["username"]}")
                    log.info("dockerUsername    : ${mapMessage["dockerUsername"]}")
                    log.info("prefix            : ${mapMessage["prefix"]}")
                    log.info("============================================")

                    def softwareManager = new SoftwareManager(mapMessage["username"], mapMessage["dockerUsername"], mapMessage["prefix"], mapMessage["id"])

                    def repositoryManagerExist = false
                    for (SoftwareManager elem : repositoryManagerThread.repositoryManagers) {

                        // Check if the software manager already exists
                        if (softwareManager.gitHubManager.getClass().getName() == elem.gitHubManager.getClass().getName() &&
                                softwareManager.gitHubManager.username == elem.gitHubManager.username &&
                                softwareManager.dockerHubManager.username == elem.dockerHubManager.username) {

                            repositoryManagerExist = true

                            // If the repository manager already exists and doesn't have the prefix yet, add it
                            if (!elem.prefixes.contains(mapMessage["prefix"])) {
                                elem.prefixes.add(mapMessage["prefix"])
                            }
                            break
                        }
                    }

                    // If the software manager doesn't exist, add it
                    if (!repositoryManagerExist) {
                        synchronized (repositoryManagerThread.repositoryManagers) {
                            repositoryManagerThread.repositoryManagers.add(softwareManager)
                        }
                    }

                    break
                case "refreshRepositories":
                    log.info("Refresh all the repositories")

                    repositoryManagerThread.refreshAll()
                    break
            }
        }
    }

}
