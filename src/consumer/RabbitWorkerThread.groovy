package consumer

import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.Channel
import groovy.json.JsonSlurper

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by julien 
 * Date : 10/04/15
 * Time : 12:43
 */
class RabbitWorkerThread implements Runnable{

    def mapMessage
    Channel channel
    String command
    def argsList

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
