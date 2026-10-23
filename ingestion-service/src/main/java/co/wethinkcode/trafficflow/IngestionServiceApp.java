package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

public class IngestionServiceApp {

    // Represents our clean, normalized intersection record
    public record CleanIntersection(String id, String district, String signalType, boolean active) {}

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/intersections-legacy.csv (intersections, districts, signal types data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
    }
}
