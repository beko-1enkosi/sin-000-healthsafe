package co.wethinkcode.healthsafe.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.atomic.AtomicReference;

public class EquipmentFailureConsumer {

    private final AtomicReference<EquipmentAlert> latestAlert = new AtomicReference<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public void start() {
        Thread thread = new Thread(() -> {
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
                Connection connection = factory.createConnection();
                connection.start();

                Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                Queue queue = session.createQueue(MqConfig.QUEUE);
                MessageConsumer consumer = session.createConsumer(queue);

                System.out.println("Listening for equipment failures...");

                while (true) {
                    Message message = consumer.receive();

                    try {
                        if (message instanceof TextMessage textMessage) {
                            EquipmentAlert alert = mapper.readValue(textMessage.getText(), EquipmentAlert.class);
                            latestAlert.set(alert);
                            message.acknowledge();

                            System.out.println("Processed equipment failure: " + textMessage.getText());
                        }
                    } catch (Exception e) {
                        System.out.println("Could not process equipment failure: " + e.getMessage());
                        session.recover();
                    }
                }

            } catch (Exception e) {
                System.out.println("Equipment consumer error: " + e.getMessage());
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    public EquipmentAlert getLatestAlert() {
        return latestAlert.get();
    }

    public record EquipmentAlert(
            String wardId,
            String department,
            String equipment,
            String description
    ) {}
}