package src.be.cytomine.software.consumer

import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.Channel
import groovy.json.JsonSlurper

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
/*
 * Copyright (c) 2009-2015. Authors: see NOTICE file.
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
class ThreadQueueCommunication implements Runnable{

    Channel channel
    String queueName
    String exchangeName

    @Override
    void run() {

        JsonSlurper slurper = new JsonSlurper()

        try {
            channel.exchangeDeclare(exchangeName, "direct", true)
            channel.queueDeclare(queueName, true, false, false, null)
            channel.queueBind(queueName, exchangeName, "")
        } catch(IOException e) {
            e.printStackTrace()
        }

        QueueingConsumer consumer = new QueueingConsumer(channel)
        channel.basicConsume(queueName, true, consumer)

        while(true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery()
            String message = new String(delivery.getBody())

            // Launch a new thread (listening on the new queue)
            def mapMessage = slurper.parseText(message)
            Runnable rabbitWorkerThread = new RabbitWorkerThread(mapMessage: mapMessage, channel: channel)
            ExecutorService execute = Executors.newSingleThreadExecutor()
            execute.execute(rabbitWorkerThread)
        }

    }
}
