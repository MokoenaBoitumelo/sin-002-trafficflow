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