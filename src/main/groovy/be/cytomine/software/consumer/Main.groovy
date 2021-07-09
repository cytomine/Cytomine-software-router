package be.cytomine.software.consumer

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

import be.cytomine.client.Cytomine
import be.cytomine.client.CytomineException
import be.cytomine.client.collections.Collection
import be.cytomine.client.collections.SoftwareCollection
import be.cytomine.client.models.ProcessingServer
import be.cytomine.client.models.Software
import be.cytomine.client.models.SoftwareUserRepository
import be.cytomine.client.models.User
import be.cytomine.software.consumer.threads.CommunicationThread
import be.cytomine.software.consumer.threads.ProcessingServerThread
import be.cytomine.software.repository.SoftwareManager
import be.cytomine.software.repository.threads.RepositoryManagerThread
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import org.apache.log4j.PropertyConfigurator

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Log4j
class Main {

    static def configFile = new ConfigSlurper().parse(new File("config.groovy").toURI().toURL())

    static Cytomine cytomine
    static Connection connection
    static Channel channel

    static def pendingPullingTable = []

    static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");

        log.info("PATH : ${System.getenv("PATH")}")

        // Create the directory for logs
        def logsDirectory = new File((String) configFile.cytomine.software.path.jobs)
        if (!logsDirectory.exists()) logsDirectory.mkdirs()

        // Create the directory for software data
        def dataDirectory = new File((String) configFile.cytomine.software.path.softwareSources)
        if (!dataDirectory.exists()) dataDirectory.mkdirs()
        def tmpDataDirectory = new File((String) configFile.cytomine.software.path.softwareSources+"/tmp")
        if (!tmpDataDirectory.exists()) tmpDataDirectory.mkdirs()

        // Create the directory for images
        def imagesDirectory = new File((String) configFile.cytomine.software.path.softwareImages)
        if (!imagesDirectory.exists()) imagesDirectory.mkdirs()




        int i = 0;
        boolean success = false
        log.info "try to connect to cytomine core api"
        while ( i < Main.configFile.cytomine.software.ssh.maxRetries && !success) {
            // Cytomine instance
            Cytomine.connection(configFile.cytomine.core.url as String, configFile.cytomine.core.publicKey as String, configFile.cytomine.core.privateKey as String)
            cytomine = Cytomine.getInstance()
            try {
                User user = cytomine.getCurrentUser()
                log.info "$user ${user.getId()} ${(user.getId() != null)}"
                success = (user.getId() != null)
                if (!success) {
                    throw new Exception("Cannot reach core")
                }
            } catch(Exception e) {
                log.warn e.toString()
                log.warn "connection not found with Cytomine ... retry"
                e.printStackTrace()
                sleep(2*60*1000)
                i++
            }
        }

        if (!success) {
            throw new Exception("Cannot connect to cytomine core api")
        }

        ping()

        log.info("Launch repository thread")
        def repositoryManagementThread = launchRepositoryManagerThread()

        log.info("Create rabbitMQ connection")
        createRabbitMQConnection()

        log.info("Launch communication thread")
        launchCommunicationThread(repositoryManagementThread)

        log.info("Launch processing server threads")
        launchProcessingServerQueues()
    }

    static void ping() {
        int limit = 20
        int i=0
        while (i < limit){
            try {
                User current = cytomine.getCurrentUser()
                if(current.getId() != null) {
                    log.info("Connected as " + current.get("username"))
                    break
                }
                sleep(30000)
                i++
            } catch (CytomineException e) {
                log.error("Connection not established. Retry : "+i)
                sleep(30000)
                i++
            }
        }
    }

    static void createRabbitMQConnection() {
        ConnectionFactory connectionFactory = new ConnectionFactory()
        connectionFactory.setHost(configFile.rabbitmq.host as String)
        connectionFactory.setUsername(configFile.rabbitmq.username as String)
        connectionFactory.setPassword(configFile.rabbitmq.password as String)
        connection = connectionFactory.newConnection()
        channel = connection.createChannel()
    }

    static RepositoryManagerThread launchRepositoryManagerThread() {
        def repositoryManagers = []

        Collection<SoftwareUserRepository> softwareUserRepositories = Collection.fetch(SoftwareUserRepository.class);
        log.info("${softwareUserRepositories.size()} softwareUserRepositories found")
        for (int i = 0; i < softwareUserRepositories.size(); i++) {
            SoftwareUserRepository currentSoftwareUserRepository = softwareUserRepositories.get(i)

            try {
                SoftwareManager softwareManager = new SoftwareManager(
                        currentSoftwareUserRepository.getStr("username"),
                        currentSoftwareUserRepository.getStr("dockerUsername"),
                        currentSoftwareUserRepository.getStr("prefix"),
                        currentSoftwareUserRepository.getId()
                )

                def repositoryManagerExist = false
                for (SoftwareManager elem : repositoryManagers) {

                    // Check if the software manager already exists
                    if (softwareManager.gitHubManager.getClass().getName() == elem.gitHubManager.getClass().getName() &&
                            softwareManager.gitHubManager.username == elem.gitHubManager.username &&
                            softwareManager.dockerHubManager.username == elem.dockerHubManager.username &&
                            !elem.prefixes.containsKey(currentSoftwareUserRepository.getStr("prefix"))) {

                        // Add the new prefix to the prefix list
                        elem.prefixes << [(currentSoftwareUserRepository.getStr("prefix")): currentSoftwareUserRepository.getLong("id")]
                        repositoryManagerExist = true

                        // Populate the software table with existing Cytomine software
                        SoftwareCollection softwareCollection = SoftwareCollection.fetchBySoftwareUserRepository(currentSoftwareUserRepository)
                        for (int j = 0; j < softwareCollection.size(); j++) {
                            Software currentSoftware = softwareCollection.get(j)
                            def key = currentSoftwareUserRepository.getStr("prefix").trim().toLowerCase() + currentSoftwareUserRepository.getStr("name").trim().toLowerCase()

                            log.info key

                            try {
                                if (currentSoftware && !currentSoftware?.getBool('deprecated')) {
                                    // Add an entry for a specific software
                                    elem.softwareTable.put(key, currentSoftware)
                                }
                            }
                            catch(Exception ignored) {}

                        }

                        break
                    }
                }

                // If the software manager doesn't exist, add it
                if (!repositoryManagerExist) {
                    // Populate the software table with existing Cytomine software
                    SoftwareCollection softwareCollection = SoftwareCollection.fetchBySoftwareUserRepository(currentSoftwareUserRepository)
                    for (int j = 0; j < softwareCollection.size(); j++) {
                        Software currentSoftware = softwareCollection.get(j)
                        def key = currentSoftwareUserRepository.getStr("prefix").trim().toLowerCase() + currentSoftware.getStr("name").trim().toLowerCase()

                        log.info key
                        try {
                            if (currentSoftware && !currentSoftware?.getBool('deprecated')) {
                                // Add an entry for a specific software
                                softwareManager.softwareTable.put(key, currentSoftware)
                            }
                        }
                        catch(Exception ignored) {}
                    }

                    // Add the software manager
                    repositoryManagers.add(softwareManager)
                }

            } catch (CytomineException ex) {
                log.info("Cytomine exception : ${ex.getMessage()}")
            } catch (Exception ex) {
                log.info("An unknown exception occurred : ${ex.getMessage()}")
            }
        }

        // Launch the repository manager thread
        def repositoryManagerThread = new RepositoryManagerThread(repositoryManagers: repositoryManagers as ArrayList)
        def executorService = Executors.newSingleThreadExecutor()
        executorService.execute(repositoryManagerThread)

        return repositoryManagerThread
    }

    static void launchCommunicationThread(RepositoryManagerThread repositoryManagerThread) {
        Runnable communicationThread = new CommunicationThread(
                repositoryManagerThread: repositoryManagerThread,
                channel: channel,
                queueName: configFile.cytomine.software.communication.queue as String,
                exchangeName: configFile.cytomine.software.communication.exchange as String
        )
        ExecutorService executorService = Executors.newSingleThreadExecutor()
        executorService.execute(communicationThread)
    }

    static void launchProcessingServerQueues() {
        JsonSlurper jsonSlurper = new JsonSlurper()

        Collection<ProcessingServer> processingServerCollection = Collection.fetch(ProcessingServer.class);
        for (int i = 0; i < processingServerCollection.size(); i++) {
            def queue = jsonSlurper.parseText(processingServerCollection.get(i).getStr("amqpQueue"))

            Runnable processingServerThread = new ProcessingServerThread(
                    channel,
                    queue,
                    processingServerCollection.get(i)
            )
            ExecutorService executorService = Executors.newSingleThreadExecutor()
            executorService.execute(processingServerThread)
        }
    }

}
