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

        // Expose cleaned records
        app.get("/intersections", ctx -> {
            List<CleanIntersection> cleanedIntersections = loadAndCleanCsv();
            ctx.json(cleanedIntersections);
        });
    }

    public static List<CleanIntersection> loadAndCleanCsv() {
        Map<String, CleanIntersection> uniqueIntersections = new LinkedHashMap<>();

        try (InputStream inputStream = IngestionServiceApp.class.getClassLoader().getResourceAsStream("intersections-legacy.csv")) {
            if (inputStream == null) {
                System.err.println("Could not find intersections-legacy.csv in resources.");
                return Collections.emptyList();
            }

            try (Reader reader = new InputStreamReader(inputStream);
                 CSVReader csvReader = new CSVReader(reader)) {

                List<String[]> rows = csvReader.readAll();
                if (rows.isEmpty()) {
                    return Collections.emptyList();
                }

                // Assuming first row is header: intersection_id,District ,signal_type,active_flag
                for (int i = 1; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    if (row.length < 4) continue;

                    String rawId = row[0];
                    String rawDistrict = row[1];
                    String rawSignalType = row[2];
                    String rawActiveFlag = row[3];

                    // 1. Clean and normalize ID (trim whitespace, preserve original or standard uppercase)
                    String id = cleanString(rawId);
                    if (id == null || id.isEmpty()) continue;

                    // 2. Clean District (trim padding, check for placeholders)
                    String district = cleanString(rawDistrict);

                    // 3. Clean Signal Type (trim padding, lowercase for consistency, check placeholders)
                    String signalType = cleanString(rawSignalType);
                    if (signalType != null) {
                        signalType = signalType.toLowerCase();
                    }

                    // 4. Normalize Active Flag (Y/yes/1/true -> true, else false)
                    boolean active = parseBoolean(rawActiveFlag);

                    // Use uppercase/standardized ID as the key for deduplication
                    String dedupKey = id.toUpperCase();

                    // Create cleaned record
                    CleanIntersection intersection = new CleanIntersection(id.toUpperCase(), district, signalType, active);

                    // Collapse duplicates (latest or first encountered; here we keep/update)
                    uniqueIntersections.put(dedupKey, intersection);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>(uniqueIntersections.values());
    }

    private static String cleanString(String value) {
        if (value == null) {
            return null;
        }
        // Trim leading/trailing whitespace and collapse double spaces
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty() ||
                cleaned.equalsIgnoreCase("N/A") ||
                cleaned.equalsIgnoreCase("unknown") ||
                cleaned.equalsIgnoreCase("TBD") ||
                cleaned.equals("-") ||
                cleaned.equalsIgnoreCase("NaN")) {
            return null;
        }
        return cleaned;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) return false;
        String val = value.trim().toLowerCase();
        return val.equals("y") ||
                val.equals("yes") ||
                val.equals("1") ||
                val.equals("true");
    }
}
