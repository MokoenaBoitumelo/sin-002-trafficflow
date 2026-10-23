package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {

    public record CongestionLevel(int level) {}

    // City-wide congestion level initialized to 0 (range 0-8)
    private static final AtomicInteger currentCongestion = new AtomicInteger(0);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the city-wide Congestion Level (0-8).)
        // Add domain endpoints for congestion-service here.

        // Get current congestion level
        app.get("/congestion", ctx -> {
            ctx.json(new CongestionLevel(currentCongestion.get()));
        });

        // Update congestion level (0 to 8)
        app.post("/congestion", ctx -> {
            try {
                CongestionLevel payload = ctx.bodyAsClass(CongestionLevel.class);
                int level = payload.level();
                if (level < 0 || level > 8) {
                    ctx.status(400).result("Congestion level must be between 0 and 8.");
                    return;
                }
                currentCongestion.set(level);
                ctx.json(new CongestionLevel(level));
            } catch (Exception e) {
                ctx.status(400).result("Invalid request body. Expected JSON: {\"level\": <0-8>}");
            }
        });
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
