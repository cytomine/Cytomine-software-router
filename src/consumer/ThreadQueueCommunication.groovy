package consumer

import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import groovy.json.JsonSlurper

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Created by julien 
 * Date : 16/04/15
 * Time : 14:04
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
