0. Prerequisites & Environment Setup
   Before writing code, ensure your execution environment is configured:

Verify Tooling:

Java 17+ (java -version)

Maven 3.8+ (mvn -version)

Docker Engine / Docker Desktop (for the ActiveMQ broker in common/)

Start the Infrastructure:

Navigate to common/ and start ActiveMQ via Docker:

Bash
cd common && docker compose up -d
Verify Build Environment:

Run a test build from the root directory to ensure the scaffold compiles:

Bash
find . -name pom.xml -execdir mvn clean package \;



Stage 1: Data Ingestion & Cleanup (ingestion-service)Goal: Read messy CSV data, clean it, and expose it via a REST endpoint.Target Service: ingestion-service (Port 7020)File to modify: IngestionServiceApp.javaData Source: src/main/resources/intersections-legacy.csvAction ItemsInspect README & Data:Open ingestion-service/README.md to identify specific CSV cleaning requirements (e.g., trim whitespace, handle missing/null fields, normalize casing, filter invalid rows).Implement CSV Parsing & Cleaning Logic:Parse intersections-legacy.csv into Java objects/records (e.g., IntersectionRecord).Apply necessary transformations (ID formatting, district validation, boolean conversion for active).Expose REST Endpoint:Implement a GET endpoint using Javalin:GET /intersections $\rightarrow$ returns 200 OK with JSON array of cleaned records.Verification:Bashcd ingestion-service && mvn package && java -jar target/ingestion-service.jar
curl http://localhost:7020/intersections




Stage 2: Core Domain Services & Synchronous REST WiringGoal: Implement business domain logic and connect services via HTTP REST calls.Target Services:intersection-service (Port 7021)congestion-service (Port 7022)routing-service (Port 7023)Action ItemsA. intersection-service (Port 7021)Load Canonical Data:On startup, issue an HTTP GET call to http://localhost:7020/intersections (ingestion-service) to load canonical records into memory.Expose Endpoint:GET /intersections/{id} $\rightarrow$ returns 200 OK with record JSON if found, or 404 Not Found if invalid.B. congestion-service (Port 7022)Manage State:Maintain an in-memory variable representing current city-wide congestion (level 0-8).Expose Endpoints:GET /congestion $\rightarrow$ returns 200 OK with {"level": <0-8>}.(Optional) Endpoint/mechanism to update/mutate congestion level for testing.C. routing-service (Port 7023)Implement Route Estimation Endpoint:GET /route (or similar endpoint taking origin/destination IDs).Synchronous Dependency Calls (REST):Call intersection-service (GET /intersections/{id}) to validate endpoint existence. Return error if 404.Call congestion-service (GET /congestion) per-request to obtain current congestion level.Calculate Travel Time:Compute estimated travel time combining route distance/speed with the congestion level modifier.




Stage 3: Event-Driven Decoupling via ActiveMQ Topic (Stretch)
Goal: Replace synchronous REST polling between Congestion and Routing with an asynchronous MQ publisher/subscriber model.

Target Services: congestion-service, routing-service, common/

Action Items
Review Shared Config:

Inspect common/README.md and MqConfig class to obtain ActiveMQ broker URL and topic name (congestion-topic).

Update congestion-service (Publisher):

Initialize ActiveMQ JMS TopicPublisher.

Whenever the congestion level changes, publish a message containing {"level": <0-8>} to congestion-topic.

Update routing-service (Subscriber):

Initialize ActiveMQ JMS TopicSubscriber listening to congestion-topic.

Maintain local state of current congestion level based on incoming topic messages instead of calling GET /congestion over REST.