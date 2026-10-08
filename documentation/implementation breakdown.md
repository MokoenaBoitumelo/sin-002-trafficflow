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