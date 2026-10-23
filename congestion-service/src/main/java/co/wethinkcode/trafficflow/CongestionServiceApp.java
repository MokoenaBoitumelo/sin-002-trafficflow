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
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)
