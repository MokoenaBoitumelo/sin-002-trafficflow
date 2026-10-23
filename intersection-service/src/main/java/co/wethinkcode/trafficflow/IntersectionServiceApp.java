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
}

// MQ TODO: publishes a periodic heartbeat to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at
// MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig), consumed by intersection-watchdog.
