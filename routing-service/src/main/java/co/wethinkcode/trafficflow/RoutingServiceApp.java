package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

public class RoutingServiceApp {

    public record RouteEstimate(String from, String to, int congestionLevel, double estimatedMinutes) {}
    public record CongestionLevel(int level) {}

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache updated via ActiveMQ subscription
    private static final AtomicInteger cachedCongestionLevel = new AtomicInteger(0);

    public static void main(String[] args) {
        initMessageSubscriber();

        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        // Estimate route travel time using cached congestion level
        app.get("/routes/estimate", ctx -> {
            String fromId = ctx.queryParam("from");
            String toId = ctx.queryParam("to");

            if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
                ctx.status(400).result("Missing required query parameters: 'from' and 'to'");
                return;
            }

            // 1. Validate 'from' intersection with Intersection Service
            if (!validateIntersection(fromId)) {
                ctx.status(404).result("Invalid or unknown 'from' intersection: " + fromId);
                return;
            }

            // 2. Validate 'to' intersection with Intersection Service
            if (!validateIntersection(toId)) {
                ctx.status(404).result("Invalid or unknown 'to' intersection: " + toId);
                return;
            }

            // 3. Retrieve congestion level from local cache (decoupled via ActiveMQ)
            int congestionLevel = cachedCongestionLevel.get();

            // 4. Calculate estimated travel time
            double baseMinutes = 10.0;
            double estimatedMinutes = baseMinutes * (1.0 + (congestionLevel * 0.15));

            ctx.json(new RouteEstimate(fromId.toUpperCase(), toId.toUpperCase(), congestionLevel, estimatedMinutes));
        });
    }

    private static void initMessageSubscriber() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createTopic(MqConfig.TOPIC);
            MessageConsumer consumer = session.createConsumer(destination);

            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        String json = textMessage.getText();
                        CongestionLevel cl = objectMapper.readValue(json, CongestionLevel.class);
                        cachedCongestionLevel.set(cl.level());
                        System.out.println("Received congestion update from topic. Cached level updated to: " + cl.level());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing incoming congestion message:");
                    e.printStackTrace();
                }
            });

            System.out.println("Subscribed to ActiveMQ topic: " + MqConfig.TOPIC);
        } catch (Exception e) {
            System.err.println("Could not connect to ActiveMQ broker for subscription:");
            e.printStackTrace();
        }
    }

    private static boolean validateIntersection(String id) {
        String url = "http://localhost:7021/intersections/" + id.trim().toUpperCase();
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}