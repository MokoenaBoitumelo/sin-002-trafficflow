package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RoutingServiceApp {

    public record RouteEstimate(String from, String to, int congestionLevel, double estimatedMinutes) {}
    public record CongestionLevel(int level) {}

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides estimated travel times based on congestion and intersection.)
        // Add domain endpoints for routing-service here.

        // Estimate route travel time
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

            // 3. Fetch current congestion level from Congestion Service
            int congestionLevel = fetchCongestionLevel();

            // 4. Calculate estimated travel time (Base 10 minutes + congestion multiplier)
            double baseMinutes = 10.0;
            double estimatedMinutes = baseMinutes * (1.0 + (congestionLevel * 0.15));

            ctx.json(new RouteEstimate(fromId.toUpperCase(), toId.toUpperCase(), congestionLevel, estimatedMinutes));
        });
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
