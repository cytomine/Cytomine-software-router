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
class RabbitWorkerThread implements Runnable{

    def mapMessage
    Channel channel

    @Override
    void run() {
        JsonSlurper slurper = new JsonSlurper()

        QueueingConsumer consumer = new QueueingConsumer(channel);
        channel.basicConsume(mapMessage["name"] as String, true, consumer);

        while(true) {

            println "Thread waiting on queue : "+ mapMessage["name"]

            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            String innerMessage = new String(delivery.getBody());

            ArrayList args = slurper.parseText(innerMessage) as ArrayList


            //String job = args.join(" ")

            //command = args[0]
            //argsList = args.subList(1, args.size())

            Runnable jobExecution = new JobExecutionThread(commandToExecute: args, softwareName: mapMessage["name"])
            ExecutorService execute = Executors.newSingleThreadExecutor();
            execute.execute(jobExecution)
        }
    }

}
