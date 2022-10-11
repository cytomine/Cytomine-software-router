package be.cytomine.software.consumer.threads

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

import be.cytomine.client.models.ProcessingServer
import be.cytomine.client.models.Software
import be.cytomine.software.consumer.Main
import be.cytomine.software.management.SingleSoftwareManager
import be.cytomine.software.repository.SoftwareManager
import be.cytomine.software.repository.threads.ImagePullerThread
import be.cytomine.software.repository.threads.RepositoryManagerThread
import be.cytomine.software.util.Utils
import com.rabbitmq.client.Channel
import com.rabbitmq.client.QueueingConsumer
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

import java.nio.file.Files
import java.nio.file.Paths
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
            try {
                log.info("[Communication] Thread waiting on queue : " + queueName)

                // Waiting for a new message
                QueueingConsumer.Delivery delivery = consumer.nextDelivery()
                String message = new String(delivery.getBody())
                log.info("[Communication] Received message: ${message}")

                def mapMessage = jsonSlurper.parseText(message)
                switch (mapMessage["requestType"]) {
                    case "addProcessingServer":
                        log.info("[Communication] Add a new processing server : " + mapMessage["name"])

                        ProcessingServer processingServer = new ProcessingServer().fetch(mapMessage["processingServerId"] as Long)

                        // Launch the processingServerThread associated to the upon processingServer
                        Runnable processingServerThread = new ProcessingServerThread(channel, mapMessage, processingServer)
                        ExecutorService executorService = Executors.newSingleThreadExecutor()
                        executorService.execute(processingServerThread)
                        break
                    case "addSoftwareUserRepository":
                        log.info("[Communication] Add a new software user repository")
                        log.info("============================================")
                        log.info("username          : ${mapMessage["username"]}")
                        log.info("dockerUsername    : ${mapMessage["dockerUsername"]}")
                        log.info("prefix            : ${mapMessage["prefix"]}")
                        log.info("============================================")

                        def connectOpts = Main.buildConnectOpts(null)
                        def softwareManager = new SoftwareManager(mapMessage["username"], mapMessage["dockerUsername"], mapMessage["prefix"], mapMessage["id"], connectOpts)


                        def repositoryManagerExist = false
                        for (SoftwareManager elem : repositoryManagerThread.repositoryManagers) {

                            // Check if the software manager already exists
                            if (softwareManager.gitHubManager.getClass().getName() == elem.gitHubManager.getClass().getName() &&
                                    softwareManager.gitHubManager.username == elem.gitHubManager.username &&
                                    softwareManager.dockerHubManager.username == elem.dockerHubManager.username) {

                                repositoryManagerExist = true

                                // If the repository manager already exists and doesn't have the prefix yet, add it
                                if (!elem.prefixes.containsKey(mapMessage["prefix"])) {
                                    elem.prefixes << [(mapMessage["prefix"]): mapMessage["id"]]
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

                        // Refresh all after add
                        repositoryManagerThread.refreshAll()

                        break
                    case "removeSoftwareUserRepository":
                        log.info("[Communication] Remove a software user repository")
                        log.info("============================================")
                        log.info("username          : ${mapMessage["username"]}")
                        log.info("dockerUsername    : ${mapMessage["dockerUsername"]}")
                        log.info("prefix            : ${mapMessage["prefix"]}")
                        log.info("============================================")
                        boolean success = repositoryManagerThread.repositoryManagers.remove(new SoftwareManager(mapMessage["username"], mapMessage["dockerUsername"], mapMessage["prefix"], mapMessage["id"], Main.buildConnectOpts(null)))
                        log.info("SoftwareManager removed : ${success}")
                        break
                    case "refreshSoftwareUserRepositoryList":
                        log.info("[Communication] Re-fetch software user repositories, it has changed")
                        sleep(3000)
                        def repositoryManagers = Main.createRepositoryManagers()

                        synchronized (repositoryManagerThread.repositoryManagers) {
                            repositoryManagerThread.repositoryManagers = repositoryManagers
                        }

                        // Refresh all after add
                        repositoryManagerThread.refreshAll()

                        break
                    case "refreshRepository":
                        log.info("[Communication] Refresh software user repository: ${mapMessage["username"]}")
                        repositoryManagerThread.refresh(mapMessage["username"])

                        break
                    case "refreshRepositories":
                        log.info("[Communication] Refresh all software user repositories")

                        repositoryManagerThread.refreshAll()
                        break
                    case "addSoftware":
                        log.info("[Communication] Add a new software")
                        log.info("============================================")
                        log.info("software_id          : ${mapMessage["SoftwareId"]}")
                        log.info("============================================")

                        Software software = new Software().fetch(mapMessage["SoftwareId"] as Long)

                        String downloadedPath = (Main.configFile.cytomine.software.path.softwareSources as String) + "/tmp/" + software.getId() + ".zip"
                        String sourcePath = (Main.configFile.cytomine.software.path.softwareSources as String) + "/" + software.getId() + ".zip"
                        software.download(downloadedPath)

                        if (!Files.probeContentType(Paths.get(downloadedPath)).contains("application/zip")) {
                            log.error "source file is not a zip file. Skipped"
                            continue
                        }
                        Utils.executeProcess("mv ${downloadedPath} ${sourcePath}", ".")

                        String version = software.get("softwareVersion")
                        def softwareManager = new SingleSoftwareManager(mapMessage["SoftwareId"] as Long, version, new File(sourcePath))
                        def result = softwareManager.installSoftware()

                        Closure callback = { softwareManager.cleanFiles() }
                        def imagePullerThread = new ImagePullerThread(pullingCommand: result.getStr("pullingCommand") as String, callback: callback)
                        ExecutorService executorService = Executors.newSingleThreadExecutor()
                        executorService.execute(imagePullerThread)

                        break
                }
            } catch(Throwable throwable) {
                throwable.printStackTrace()
                log.error("Error during processing:"+ throwable)
            }
        }
    }

}
