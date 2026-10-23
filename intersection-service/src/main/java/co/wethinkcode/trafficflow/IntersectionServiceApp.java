package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IntersectionServiceApp {

    public record CleanIntersection(String id, String district, String signalType, boolean active) {}

    private static final Map<String, CleanIntersection> intersectionStore = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Fetch and load intersections on startup
        loadIntersectionsFromIngestionService();

        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Validates intersection/district names (source of truth).)
        // Add domain endpoints for intersection-service here.

        // Get all intersections
        app.get("/intersections", ctx -> {
            ctx.json(new ArrayList<>(intersectionStore.values()));
        });

        // Get single intersection by ID
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            CleanIntersection intersection = intersectionStore.get(id);
            if (intersection == null) {
                ctx.status(404).result("Intersection not found: " + id);
            } else {
                ctx.json(intersection);
            }
        });
    }

    private static void loadIntersectionsFromIngestionService() {
        String ingestionUrl = "http://localhost:7020/intersections";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ingestionUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                List<CleanIntersection> list = mapper.readValue(
                        response.body(),
                        new TypeReference<List<CleanIntersection>>() {}
                );
                for (CleanIntersection item : list) {
                    intersectionStore.put(item.id().toUpperCase(), item);
                }
                System.out.println("Successfully loaded " + intersectionStore.size() + " intersections from ingestion-service.");
            } else {
                System.err.println("Failed to load intersections. Status code: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Could not connect to ingestion-service at " + ingestionUrl + ". Ensure ingestion-service is running.");
        }
    }
}

// MQ TODO: publishes a periodic heartbeat to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at
// MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig), consumed by intersection-watchdog.
