package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {

    public record CongestionLevel(int level) {}

    private static final AtomicInteger currentCongestion = new AtomicInteger(0);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ActiveMQ producer objects
    private static Connection mqConnection;
    private static Session mqSession;
    private static MessageProducer mqProducer;

    public static void main(String[] args) {
        initMessageProducer();

        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        // Get current congestion level
        app.get("/congestion", ctx -> {
            ctx.json(new CongestionLevel(currentCongestion.get()));
        });

        // Update congestion level and publish to ActiveMQ topic
        app.post("/congestion", ctx -> {
            try {
                CongestionLevel payload = ctx.bodyAsClass(CongestionLevel.class);
                int level = payload.level();
                if (level < 0 || level > 8) {
                    ctx.status(400).result("Congestion level must be between 0 and 8.");
                    return;
                }
                currentCongestion.set(level);

                // Publish update to ActiveMQ topic
                publishCongestionUpdate(level);

                ctx.json(new CongestionLevel(level));
            } catch (Exception e) {
                ctx.status(400).result("Invalid request body. Expected JSON: {\"level\": <0-8>}");
            }
        });
    }

    private static void initMessageProducer() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            mqConnection = connectionFactory.createConnection();
            mqConnection.start();
            mqSession = mqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = mqSession.createTopic(MqConfig.TOPIC);
            mqProducer = mqSession.createProducer(destination);
            System.out.println("Connected to ActiveMQ broker for publishing on topic: " + MqConfig.TOPIC);
        } catch (Exception e) {
            System.err.println("Warning: Could not connect to ActiveMQ broker. Publishing will fail until broker is up.");
            e.printStackTrace();
        }
    }

    private static void publishCongestionUpdate(int level) {
        if (mqSession == null || mqProducer == null) {
            System.err.println("MQ Session/Producer not initialized. Skipping message publish.");
            return;
        }
        try {
            CongestionLevel payload = new CongestionLevel(level);
            String jsonMessage = objectMapper.writeValueAsString(payload);
            TextMessage message = mqSession.createTextMessage(jsonMessage);
            mqProducer.send(message);
            System.out.println("Published congestion update to topic: " + jsonMessage);
        } catch (Exception e) {
            System.err.println("Failed to publish congestion update message:");
            e.printStackTrace();
        }
    }
}