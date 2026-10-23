# Project Codebase Export

## File: `./.gitignore`

```text
target
dependency-reduced-pom.xml
*.class
.idea/
*.iml
.vscode/
.DS_Store

```

## File: `./README.md`

```text
# TrafficFlow

## Overview

Urban traffic light control and congestion-based routing.

Domain entities: intersections, districts, signal types.

Every class in this repo lives in a single flat package, `co.wethinkcode.trafficflow`. TrafficFlow is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`intersections-legacy.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`congestion-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **IntersectionWatchdogApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7020 | Parses and cleans `intersections-legacy.csv` |
| IntersectionServiceApp | [`intersection-service/`](intersection-service) | 7021 | Validates intersection/district names (source of truth). |
| CongestionServiceApp | [`congestion-service/`](congestion-service) | 7022 | Tracks the city-wide Congestion Level (0-8). |
| RoutingServiceApp | [`routing-service/`](routing-service) | 7023 | Provides estimated travel times based on congestion and intersection. |
| IntersectionWatchdogApp | [`intersection-watchdog/`](intersection-watchdog) | 7024 | cries for help if the Intersection Service crashes, since routes can no longer be validated. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `congestion-topic`: Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

**Status:** scaffold only — build files, Javalin bootstrap, and TODOs are in place; no
business logic has been implemented yet.

## Your task

Build out the four stages below, roughly in order — each stage builds on the last.
Stages 1-2 are the core exercise; stages 3-4 are stretch goals if you have time left.
Exact field names and response shapes are your call throughout — see
"Integration contracts" below for illustrative shapes, not a spec to match exactly.

| Stage | What | Required? | Rough effort |
|---|---|---|---|
| 1 | Clean `intersections-legacy.csv` in **IngestionServiceApp** and expose the cleaned records (see [`ingestion-service/README.md`](ingestion-service) for the specific data issues to handle) | Required | 1-2 hrs |
| 2 | Implement the domain endpoints in **IntersectionServiceApp**, **CongestionServiceApp**, and **RoutingServiceApp**, wired together with direct synchronous REST calls | Required | 2-3 hrs |
| 3 | Decouple Congestion → Routing with the `congestion-topic` ActiveMQ topic instead of a direct REST call (see [`common/README.md`](common)) | Stretch | 1 hr |
| 4 | Add heartbeat/dead-letter alerting in **IntersectionWatchdogApp** so it notices when the Intersection Service goes down | Stretch | 1 hr |

(Effort is a rough guide, not a hard budget — go with what feels right for your pace.)

## Integration contracts

Every place one service calls or messages another, with an illustrative shape.
None of these field names are binding — match the intent, not the exact JSON.

| From → To | Stage | Mechanism | Shape |
|---|---|---|---|
| ingestion-service → intersection-service | 1 | REST, `GET` | `GET /intersections` on ingestion-service (port 7020) → `200 OK` + JSON array of cleaned records, e.g. `[{"id": "INT-1001", "district": "Downtown", "signalType": "4-way", "active": true}, ...]`. intersection-service loads this as its canonical list. |
| routing-service → intersection-service | 2 | REST, `GET` | `GET /intersections/{id}` on intersection-service (port 7021) → `200 OK` with the record, or `404` if the id/district isn't recognized. routing-service calls this to validate a route's endpoints before estimating travel time. |
| routing-service → congestion-service | 2 | REST, `GET` | `GET /congestion` on congestion-service (port 7022) → `200 OK` + `{"level": 0-8}`. routing-service polls this per-request in stage 2. |
| congestion-service → routing-service | 3 | ActiveMQ Topic `congestion-topic` | Once stage 3 is in place, congestion-service publishes `{"level": 0-8}` to `congestion-topic` whenever the level changes, and routing-service subscribes instead of polling `GET /congestion`. Broker URL + topic name come from the shared `MqConfig` class — see [`common/README.md`](common). |
| intersection-service → intersection-watchdog | 4 | ActiveMQ Queue `intersection-heartbeat-queue` | intersection-service publishes a periodic heartbeat message to `intersection-heartbeat-queue`; intersection-watchdog consumes it and raises an alert (e.g. logs, or its own `/alert` state) if a heartbeat is missed or lands in the dead-letter queue. Queue name comes from the shared `MqConfig` class, same pattern as `congestion-topic` — see [`intersection-watchdog/README.md`](intersection-watchdog). |

## Project structure

```
trafficflow/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7020)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/trafficflow/IngestionServiceApp.java
│       └── resources/intersections-legacy.csv
├── intersection-service/          (port 7021)
├── congestion-service/          (port 7022)
├── routing-service/          (port 7023)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── intersection-watchdog/          (port 7024)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `intersection-watchdog/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd intersection-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn -q package \;
```

## Run

```
# ingestion
cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

# domain services, each in its own terminal
# terminal 1
cd intersection-service && mvn package && java -jar target/intersection-service.jar
# terminal 2
cd congestion-service && mvn package && java -jar target/congestion-service.jar
# terminal 3
cd routing-service && mvn package && java -jar target/routing-service.jar

# MQ broker (needed once the MQ-aware services above are wired up)
cd common && docker compose up -d

# alerting
cd intersection-watchdog && mvn package && java -jar target/intersection-watchdog.jar
```

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7020 |
| IntersectionServiceApp (`intersection-service`) | 7021 |
| CongestionServiceApp (`congestion-service`) | 7022 |
| RoutingServiceApp (`routing-service`) | 7023 |
| IntersectionWatchdogApp (`intersection-watchdog`) | 7024 |

## Test

No automated tests exist yet (this is a scaffold). Each running service exposes
`/health`, so sanity-check manually:

```
curl http://localhost:7020/health   # -> OK
```

To add real tests to a module, add JUnit 5 and Surefire to its `pom.xml`:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
</plugin>
```

then add tests under that module's `src/test/java/...` and run:

```
mvn test
```

```

## File: `./RUBRIC.md`

```text
# TrafficFlow — Rubric

**For reviewers only. Do not share with candidates.**

Scores against the stages in the root [README.md](README.md#your-task) "Your task"
section. Stages 1-2 are required; stages 3-4 are stretch — weight accordingly if a
candidate ran out of time on the stretch stages.

## Stage 1 — Ingestion (required)

- [ ] Handles casing inconsistencies (IDs, district, signal type)
- [ ] Trims padding (leading/trailing/double spaces)
- [ ] Detects and collapses duplicate records for the same real-world intersection
- [ ] Handles missing/placeholder values explicitly (not silently dropped, not guessed)
- [ ] Normalizes the `active_flag` boolean representations (`Y`/`N`, `yes`/`no`, `1`/`0`, `true`/`FALSE`)
- [ ] Cleaned data is exposed for other services to consume (any reasonable shape)

## Stage 2 — Domain services + REST wiring (required)

- [ ] `intersection-service` exposes a way to validate/look up an intersection or district
- [ ] `congestion-service` exposes the current city-wide congestion level (0-8)
- [ ] `routing-service` calls both of the above over HTTP and produces an estimated
      travel time that's actually a function of congestion + intersection validity
      (not a hardcoded value)
- [ ] Sensible HTTP status codes / error handling for the unhappy path (unknown
      intersection, unreachable dependency, etc.)

## Stage 3 — MQ decoupling (stretch)

- [ ] `congestion-service` publishes to `congestion-topic` on level change
- [ ] `routing-service` subscribes instead of polling `congestion-service` directly
- [ ] Broker URL/topic name sourced from `MqConfig`, not hardcoded inline

## Stage 4 — Watchdog alerting (stretch)

- [ ] `intersection-service` publishes heartbeats to `intersection-heartbeat-queue`
- [ ] `intersection-watchdog` detects a missed heartbeat / dead-lettered message and
      surfaces it somehow (log, endpoint, exit code — any observable signal is fine)

## Cross-cutting (all stages)

- [ ] **Readability** — reasonable naming, no dead code, code a teammate could pick up
- [ ] **Incremental commits** — history shows the stages being built up, not one giant commit
- [ ] **Tests** — at least some automated coverage of the ingestion cleaning logic in particular
- [ ] **Tradeoffs** — candidate can articulate what they'd do differently with more
      time, and why they made the calls they did (especially around the parts the
      README deliberately left open, like exact endpoint/message shapes)

## Notes for reviewers

- Exact field names, endpoint paths, and message schemas are intentionally
  unspecified in the README — don't penalize a candidate for choosing something
  different than what's shown in the "Integration contracts" section, as long as
  it's internally consistent and the intent is right.
- `intersections-legacy.csv` (see [`ingestion-service/`](ingestion-service)) doesn't
  actually contain a date or numeric column, even though the "Known data issues"
  list in that README mentions inconsistent date formats and invalid numeric
  values as things to watch for generally. Don't dock a candidate for not handling
  those two categories against this specific file.

```

## File: `./common/docker-compose.yml`

```text
version: "3.8"
services:
  activemq:
    image: apache/activemq-classic:5.18.3
    ports:
      - "61616:61616"   # OpenWire (client connections)
      - "8161:8161"     # Web console

```

## File: `./common/README.md`

```text
# common — Asynchronous Decoupling (MQ)

## Overview

Part of the [TrafficFlow](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own. Two
independent integration points share this one broker:

### Topic: `congestion-topic`

Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

- Producer: `congestion-service` (`../congestion-service`)
- Consumer(s): `routing-service` (`../routing-service`)

Broker URL and topic name are shared via a common `co.wethinkcode.trafficflow.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

### Queue: `intersection-heartbeat-queue`

Intersection Watchdog notices when Intersection Service goes down by watching for
missed heartbeats / dead-lettered messages instead of polling its `/health` endpoint.

- Producer: `intersection-service` (`../intersection-service`)
- Consumer(s): `intersection-watchdog` (`../intersection-watchdog`)

Broker URL and queue name are shared the same way, via each service's own copy of
`co.wethinkcode.trafficflow.mq.MqConfig` (`BROKER_URL`, `HEARTBEAT_QUEUE`).

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has its own
`src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java` with the constants for
whichever topic/queue that service participates in).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Once the TODOs below are implemented, verify end-to-end by publishing a message from
`congestion-service` and confirming the consumer(s) receive it — e.g. via logs, or by
watching the topic in the web console.

## TODO

- Add `activemq-client` publish logic to `congestion-service` on its stage/state-change endpoint.
- Add `activemq-client` subscriber logic to consumer service(s) above, replacing any
  direct synchronous calls to `congestion-service`.
- Add `activemq-client` heartbeat-publish logic to `intersection-service`.
- Add `activemq-client` subscriber/alerting logic to `intersection-watchdog`.

```

## File: `./ingestion-service/README.md`

```text
# IngestionServiceApp

## Overview

Parses and cleans `intersections-legacy.csv`, a messy legacy export of intersections, districts, and signal types data, and is the
first stop in the TrafficFlow pipeline. Independent Maven module, no parent pom.

Part of the [TrafficFlow](../README.md) project.

## Known data issues

`intersections-legacy.csv` is deliberately messy — cleaning it is the point of this service. Look
out for (and handle) at least:

- **Inconsistent casing** in IDs, names, and status/category values (`Active` /
  `active` / `ACTIVE`)
- **Padding** — leading/trailing spaces, and the occasional double space, inside
  fields
- **Duplicate records** for the same real-world entity, written with a different ID
  casing/format and/or slightly different field values
- **Inconsistent date formats** (`YYYY-MM-DD`, `MM/DD/YYYY`, `DD-MM-YYYY`, one- and
  two-digit months/days) and outright invalid dates
- **Missing / placeholder values** — blank fields, `N/A`, `n/a`, `TBD`, `unknown`,
  `-`, `NaN`
- **Invalid or non-numeric values** in numeric columns (negative counts, spelled-out
  numbers, unrealistic values)
- **Inconsistent boolean/flag representations** (`Y`/`N`, `yes`/`no`, `1`/`0`,
  `true`/`FALSE`)
- **Naming/spelling variants** for the same thing (e.g. regional spelling
  differences, synonyms)

## Worked example

A few raw rows from `intersections-legacy.csv`, and one reasonable cleaned shape for
them. Your field names/casing conventions don't need to match this exactly — the
point is normalizing consistently and handling the duplicate/missing cases, not
hitting this exact JSON.

Raw:

```csv
intersection_id,District ,signal_type,active_flag
INT-1001, Downtown ,4-way,Y
INT-1005,Downtown,Roundabout,true
int-1005,downtown ,ROUNDABOUT,TRUE
INT-1007,Eastside,,1
INT-1015,,4-way,Y
```

Cleaned:

```json
[
  { "id": "INT-1001", "district": "Downtown", "signalType": "4-way",       "active": true },
  { "id": "INT-1005", "district": "Downtown", "signalType": "roundabout",  "active": true },
  { "id": "INT-1007", "district": "Eastside", "signalType": null,          "active": true },
  { "id": "INT-1015", "district": null,       "signalType": "4-way",       "active": true }
]
```

What happened:
- `INT-1001`: trimmed the padded district (`" Downtown "` → `"Downtown"`); flag `Y` → `true`.
- `INT-1005` / `int-1005`: same real-world intersection under two ID casings and two
  signal-type casings — collapsed to a single record.
- `INT-1007`: signal type was blank in the source — kept as an explicit `null` rather
  than dropped or guessed, so downstream services can see it's missing.
- `INT-1015`: same idea for a missing district.

## Project structure

```
ingestion-service/
├── pom.xml
└── src/main/
    ├── java/co/wethinkcode/trafficflow/IngestionServiceApp.java
    └── resources/intersections-legacy.csv
```

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7020`. Currently just exposes `/health` — the actual CSV
parsing/cleaning logic is a TODO.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7020/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/trafficflow/`, and run `mvn test`.

```

## File: `./ingestion-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.trafficflow</groupId>
  <artifactId>ingestion-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>com.opencsv</groupId>
      <artifactId>opencsv</artifactId>
      <version>5.9</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>ingestion-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.trafficflow.IngestionServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./ingestion-service/src/main/java/co/wethinkcode/trafficflow/IngestionServiceApp.java`

```text
package co.wethinkcode.trafficflow;

import io.javalin.Javalin;

public class IngestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO: read and clean src/main/resources/intersections-legacy.csv (intersections, districts, signal types data —
        // trim whitespace, fix casing, normalize dates/booleans) and expose the
        // cleaned records here for the other services to consume.
    }
}

```

## File: `./ingestion-service/src/main/resources/intersections-legacy.csv`

```text
intersection_id,District ,signal_type,active_flag
INT-1001, Downtown ,4-way,Y
int-1002,Midtown,pedestrian,yes
INT-1003 ,downtown,4-way,0
INT-1004,Uptown,unknown,Y
INT-1005,Downtown,Roundabout,true
int-1005,downtown ,ROUNDABOUT,TRUE
INT-1006,Midtown,4-Way,no
INT-1007,Eastside,,1
INT-1008,uptown,pedestrian,N
INT-1009,Westside,4-way,FALSE
INT-1010,Downtown ,stop-sign,Y
INT-1011,midtown,4-way,yes
int-1012,Eastside,ROUNDABOUT,0
INT-1013,Westside ,unknown,unknown
INT-1014,Uptown,Pedestrian,1
INT-1015,,4-way,Y
INT-1016,Downtown,4-way,YES
INT-1017,eastside,stop-sign,n

```

## File: `./congestion-service/README.md`

```text
# CongestionServiceApp

## Overview

Tracks the city-wide Congestion Level (0-8).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
congestion-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── CongestionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/congestion-service.jar
```

Listens on port `7022`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7022/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/trafficflow/`, and run `mvn test`.

```

## File: `./congestion-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.trafficflow</groupId>
  <artifactId>congestion-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>congestion-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.trafficflow.CongestionServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./congestion-service/src/main/java/co/wethinkcode/trafficflow/CongestionServiceApp.java`

```text
package co.wethinkcode.trafficflow;

import io.javalin.Javalin;

public class CongestionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Tracks the city-wide Congestion Level (0-8).)
        // Add domain endpoints for congestion-service here.
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)

```

## File: `./congestion-service/src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java`

```text
package co.wethinkcode.trafficflow.mq;

/**
 * Shared by every producer/consumer service that talks to the "congestion-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "congestion-topic";

    private MqConfig() {
    }
}

```

## File: `./routing-service/README.md`

```text
# RoutingServiceApp

## Overview

Provides estimated travel times based on congestion and intersection.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
routing-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── RoutingServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/routing-service.jar
```

Listens on port `7023`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7023/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/trafficflow/`, and run `mvn test`.

```

## File: `./routing-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.trafficflow</groupId>
  <artifactId>routing-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>routing-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.trafficflow.RoutingServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./routing-service/src/main/java/co/wethinkcode/trafficflow/RoutingServiceApp.java`

```text
package co.wethinkcode.trafficflow;

import io.javalin.Javalin;

public class RoutingServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides estimated travel times based on congestion and intersection.)
        // Add domain endpoints for routing-service here.
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig)

```

## File: `./routing-service/src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java`

```text
package co.wethinkcode.trafficflow.mq;

/**
 * Shared by every producer/consumer service that talks to the "congestion-topic"
 * ActiveMQ topic. Duplicated into each participating service's own source tree,
 * since these are independent Maven projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String TOPIC = "congestion-topic";

    private MqConfig() {
    }
}

```

## File: `./intersection-service/README.md`

```text
# IntersectionServiceApp

## Overview

Validates intersection/district names (source of truth).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
intersection-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-service.jar
```

Listens on port `7021`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7021/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/trafficflow/`, and run `mvn test`.

```

## File: `./intersection-service/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.trafficflow</groupId>
  <artifactId>intersection-service</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>intersection-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.trafficflow.IntersectionServiceApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./intersection-service/src/main/java/co/wethinkcode/trafficflow/IntersectionServiceApp.java`

```text
package co.wethinkcode.trafficflow;

import io.javalin.Javalin;

public class IntersectionServiceApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Validates intersection/district names (source of truth).)
        // Add domain endpoints for intersection-service here.
    }
}

// MQ TODO: publishes a periodic heartbeat to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at
// MqConfig.BROKER_URL (see co.wethinkcode.trafficflow.mq.MqConfig), consumed by intersection-watchdog.

```

## File: `./intersection-service/src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java`

```text
package co.wethinkcode.trafficflow.mq;

/**
 * Shared by the producer/consumer services that talk to the
 * "intersection-heartbeat-queue" ActiveMQ queue. Duplicated into each
 * participating service's own source tree, since these are independent Maven
 * projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String HEARTBEAT_QUEUE = "intersection-heartbeat-queue";

    private MqConfig() {
    }
}

```

## File: `./documentation/wiki.md`

```text
🚀 Project OverviewProject Name: [Insert Project Name]Target Completion Date: Day 14Core Objective: Build a functional Minimum Viable Product (MVP) focusing on core features before adding nice-to-haves.Tech Stack & ToolsLanguage/Framework: [e.g., Python, Java, Node.js]Version Control: Git & GitHub (Feature branch workflow)Project Management: GitHub Projects / Trello📅 2-Week Timeline & MilestonesWeekPhaseKey DeliverablesWeek 1Foundation & MVP• Project setup & repo initialization• Core data structures and architecture• Basic working prototype (happy path)Week 2Refinement & Polish• Edge case handling & error management• Testing (Unit/Integration tests)• Documentation & final cleanupDetailed Daily BreakdownWeek 1: Setup and Core LogicDay 1: Scaffolding & ArchitectureInitialize repository, configure build tools/dependencies, and set up the folder structure.Define data models, database schema (if applicable), and API contracts.Day 2–3: Core Features (The Engine)Implement the primary business logic.Get the core feature working end-to-end in a basic state (MVP).Day 4–5: Integration & Initial TestingConnect components together and write initial unit tests for critical logic.Fix blocking bugs and refine data flow.Week 2: Enhancement, Testing, and DeliveryDay 8–9: Secondary Features & UI/InterfaceAdd secondary features, user input handling, or CLI/GUI polish.Day 10–11: Testing & Bug SmashingPerform integration testing, handle edge cases, and fix remaining bugs.Day 12–13: Documentation & Code ReviewWrite a clear README.md (setup instructions, usage guide, tech stack).Clean up comments, format code, and ensure clean Git history.Day 14: Final Submission / DemoFinal review against project requirements and tag release version.📋 Task Backlog & Tracking[ ] Phase 1: Setup[ ] Initialize repo and .gitignore[ ] Define project structure and dependencies[ ] Phase 2: Core Development[ ] Implement core logic / algorithms[ ] Build input/output interface (CLI, API, or UI)[ ] Phase 3: Testing & Polish[ ] Write unit tests for core modules[ ] Handle errors and edge cases[ ] Phase 4: Documentation[ ] Complete README.md setup guide[ ] Add code comments and usage examples🛠️ Definition of Done (DoD)A task or feature is only considered "Done" when:Code compiles/runs without errors or unhandled warnings.Tested locally against expected inputs and basic edge cases.Committed cleanly with descriptive commit messages on a feature branch.Merged into the main branch via a reviewed Pull Request (if working in a team).
```

## File: `./documentation/implementation breakdown.md`

```text
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
```

## File: `./documentation/knowledge_base.txt`

```text

```

## File: `./intersection-watchdog/README.md`

```text
# IntersectionWatchdogApp

## Overview

Cries for help if the Intersection Service crashes, since routes can no longer be validated.

Part of the [TrafficFlow](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

MQ: this service subscribes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module. Mechanism: watch for missed heartbeats and/or dead-lettered messages from `intersection-service` and raise an alert.

## Project structure

```
intersection-watchdog/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionWatchdogApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-watchdog.jar
```

Listens on port `7024`.

## Test

No automated tests yet. Manually verify it's up:

```
curl http://localhost:7024/health   # -> OK
```

To add real tests, add JUnit 5 + the Surefire plugin to `pom.xml`, put tests under
`src/test/java/co/wethinkcode/trafficflow/`, and run `mvn test`.

```

## File: `./intersection-watchdog/pom.xml`

```text
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>co.wethinkcode.trafficflow</groupId>
  <artifactId>intersection-watchdog</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>5.6.3</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.9</version>
    </dependency>
    <dependency>
      <groupId>org.apache.activemq</groupId>
      <artifactId>activemq-client</artifactId>
      <version>5.18.3</version>
    </dependency>
  </dependencies>

  <build>
    <finalName>intersection-watchdog</finalName>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.1</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>co.wethinkcode.trafficflow.IntersectionWatchdogApp</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>

```

## File: `./intersection-watchdog/src/main/java/co/wethinkcode/trafficflow/IntersectionWatchdogApp.java`

```text
package co.wethinkcode.trafficflow;

import io.javalin.Javalin;

public class IntersectionWatchdogApp {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7024);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Cries for help if the Intersection Service crashes, since routes can no longer be validated.)
        // Mechanism: ActiveMQ Queue heartbeat/dead-letter
    }
}

// MQ TODO: subscribes to ActiveMQ queue MqConfig.HEARTBEAT_QUEUE at MqConfig.BROKER_URL
// (see co.wethinkcode.trafficflow.mq.MqConfig) and alerts if a heartbeat from
// intersection-service is missed or a message lands in the dead-letter queue.

```

## File: `./intersection-watchdog/src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java`

```text
package co.wethinkcode.trafficflow.mq;

/**
 * Shared by the producer/consumer services that talk to the
 * "intersection-heartbeat-queue" ActiveMQ queue. Duplicated into each
 * participating service's own source tree, since these are independent Maven
 * projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String HEARTBEAT_QUEUE = "intersection-heartbeat-queue";

    private MqConfig() {
    }
}

```

