package co.wethinkcode.healthsafe.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

public class StaffingEventPublisher {

    public void publish(String message) {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

        try (Connection connection = factory.createConnection()) {
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(MqConfig.TOPIC);
            MessageProducer producer = session.createProducer(topic);

            producer.send(session.createTextMessage(message));
            session.close();

            System.out.println("Published staffing event: " + message);
        } catch (Exception e) {
            System.out.println("Could not publish staffing event: " + e.getMessage());
        }
    }
}