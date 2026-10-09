Here is a complete, structured roadmap and step-by-step checklist to guide your implementation of the TrafficFlow application.

🚀 Execution Roadmap Overview
+-----------------------------+
|   intersections-legacy.csv  |
+--------------+--------------+
|
v
+---------------------------+
|   1. IngestionService     | (Port 7020)
| (Parse & Clean Legacy CSV)|
+-------------+-------------+
|
| GET /intersections (Stage 1)
v
+---------------------------+
|   2. IntersectionService  | (Port 7021)
|  (Canonical Source of Truth)
+------+--------------+-----+
^              |
GET /intersections/{id}   |              | Heartbeat Queue (Stage 4)
(Stage 2)          |              v
|    +-------------------+
|    | 4. WatchdogApp    | (Port 7024)
|    | (Alert on Down)   |
|    +-------------------+
|
+--------+-------+
| 2. RoutingService | (Port 7023)
| (Travel Estimates)|
+--------+-------+
^
REST GET /congestion (Stage 2)
|  or
ActiveMQ Topic: congestion-topic (Stage 3)
|
+--------+-------+
| 2/3. CongestionService | (Port 7022)
| (City Congestion 0-8)  |
+------------------------+
🛠️ Phase-by-Phase Checklist
Prerequisite Setup
[ ] Ensure Java 17+ and Maven 3.8+ are installed (java -version, mvn -version).

[ ] Ensure Docker Desktop / Engine is running.

[ ] Start the ActiveMQ broker shared instance:

Bash
cd common && docker compose up -d
Stage 1: Legacy Data Ingestion (ingestion-service)
Goal: Parse intersections-legacy.csv, clean corrupted/dirty records, and expose the sanitized data via REST.

[ ] Locate Resource File: Inspect ingestion-service/src/main/resources/intersections-legacy.csv.

[ ] Read Service Details: Read ingestion-service/README.md to identify all data cleaning requirements (e.g., removing whitespace, handling bad formats, filtering invalid fields/duplicates).

[ ] Build CSV Parsing Logic:

Parse raw CSV rows using standard Java IO or a lightweight CSV parser.

Sanitize entries into a canonical domain object (e.g., IntersectionRecord).

[ ] Implement Endpoint in IngestionServiceApp.java:

Route: GET /intersections

Response: 200 OK + JSON Array of cleaned intersection objects.

[ ] Verification:

Build and run: cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

Test: curl http://localhost:7020/intersections

Stage 2: Core Domain Services (REST Integration)
Goal: Implement business logic and wire the three primary services via direct synchronous REST calls.

1. intersection-service (Port 7021)
   [ ] On bootstrap/startup, fetch the initial cleaned data from ingestion-service:

GET http://localhost:7020/intersections

[ ] Store the records in memory as the canonical source of truth.

[ ] Expose Endpoints:

GET /intersections — Returns all intersections.

GET /intersections/{id} — Returns a specific record (200 OK) or 404 Not Found if the ID doesn't exist.

2. congestion-service (Port 7022)
   [ ] Manage state for the city-wide congestion index (integer value ranging from 0 to 8).

[ ] Expose Endpoints:

GET /congestion — Returns {"level": <0-8>}.

POST /congestion (or PUT /congestion) — Allows updating the congestion level.

3. routing-service (Port 7023)
   [ ] Implement Travel Time Estimation Endpoint:

POST /routes/estimate (or GET /routes/estimate?from={id}&to={id})

[ ] In-flight Validations:

Query GET http://localhost:7021/intersections/{id} to validate endpoints (from & to). Return 404/400 if invalid.

Query GET http://localhost:7022/congestion to fetch the current congestion level.

[ ] Compute travel time based on intersection distances and the retrieved congestion level.

Stage 3: Asynchronous Decoupling via ActiveMQ (Stretch Goal)
Goal: Replace REST polling between congestion-service and routing-service with event-driven Messaging via ActiveMQ.

[ ] Check common/README.md for shared broker connection details and MqConfig parameters.

[ ] Update congestion-service:

Connect to ActiveMQ using JMS (ActiveMQConnectionFactory).

Whenever the congestion level changes, publish a JSON payload {"level": X} to the congestion-topic ActiveMQ Topic.

[ ] Update routing-service:

Subscribe to congestion-topic.

Maintain an in-memory cache of the latest congestion level updated continuously via incoming topic messages.

Remove the direct HTTP call to GET http://localhost:7022/congestion.

Stage 4: Alerting & Resilience (intersection-watchdog) (Stretch Goal)
Goal: Implement health monitoring and heartbeats to raise alarms if intersection-service crashes.

[ ] Read intersection-watchdog/README.md for queue specifications.

[ ] Update intersection-service:

Schedule a background thread/task to publish a periodic heartbeat message (e.g., every 5 seconds) to intersection-heartbeat-queue.

[ ] Implement IntersectionWatchdogApp (Port 7024):

Listen to intersection-heartbeat-queue.

Track the timestamp of the last received heartbeat.

Execute a periodic check: if no heartbeat arrives within the threshold window (e.g., 15 seconds), trigger an alert state.

[ ] Expose Alert Endpoint in Watchdog:

GET /health or GET /status — Return alarm status (CRITICAL / OK).

🧪 Testing & Verification Guide
Quick Health Check
Run in separate terminal tabs or a batch script:

Bash
curl http://localhost:7020/health
curl http://localhost:7021/health
curl http://localhost:7022/health
curl http://localhost:7023/health
curl http://localhost:7024/health
End-to-End Functional Test Sequence
1. Ingestion Verification:

Bash
curl -X GET http://localhost:7020/intersections

2. Intersection Fetching:

Bash
curl -X GET http://localhost:7021/intersections/INT-1001

3. Congestion Update:

Bash
curl -X POST http://localhost:7022/congestion -H "Content-Type: application/json" -d '{"level": 5}'

4. Route Estimation:

Bash
curl -X GET "http://localhost:7023/routes/estimate?from=INT-1001&to=INT-1002"