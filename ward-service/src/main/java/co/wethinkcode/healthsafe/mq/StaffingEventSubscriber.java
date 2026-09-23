package co.wethinkcode.healthsafe.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.concurrent.atomic.AtomicReference;

public class StaffingEventSubscriber {

    private final AtomicReference<String> latestEvent = new AtomicReference<>();

    public void start() {
        Thread listenerThread = new Thread(() -> {
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
                Connection connection = factory.createConnection();
                connection.start();

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(MqConfig.TOPIC);
                MessageConsumer consumer = session.createConsumer(topic);

                System.out.println("Listening for staffing events...");

                while (true) {
                    Message message = consumer.receive();

                    if (message instanceof TextMessage textMessage) {
                        String event = textMessage.getText();
                        latestEvent.set(event);
                        System.out.println("Received staffing event: " + event);
                    }
                }

            } catch (Exception e) {
                System.out.println("Staffing subscriber error: " + e.getMessage());
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public String getLatestEvent() {
        return latestEvent.get();
    }
}