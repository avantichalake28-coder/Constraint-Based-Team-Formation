# README

## Prerequisites
- **Java 17** (or higher) installed and `JAVA_HOME` configured.
- **Maven** (3.9+) on the system path.
- **Docker** (for running MySQL via Docker Compose and for Testcontainers in the test suite).
- **Git** (optional, for version control).

## Project Structure
```
student-team-formation/
└─ backend/                # Spring Boot backend
   ├─ src/main/java/...    # Application code
   ├─ src/main/resources  # Configuration and Flyway migrations
   ├─ src/test/java/...    # Tests (uses Testcontainers)
   ├─ pom.xml
   └─ Dockerfile (optional)
```

## Setup
1. **Clone the repository** (if not already in the workspace).
2. Copy the example environment file and edit values as needed:
   ```bash
   cp .env.example .env
   ```
3. Start the MySQL container:
   ```bash
   docker-compose up -d mysql
   ```
   The container will expose port **3306**. Default credentials are `root` / `password` and database `student_team_formation`.
4. Verify the database is healthy (Docker health‑check will report `healthy`).

## Building & Running
```bash
# Build and run the application (Maven will download dependencies)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
The API will be available at `http://localhost:8081/api/v1`.

## Testing
```bash
# Run unit and integration tests (requires Docker for Testcontainers)
mvn clean verify
```
The tests include:
- Flyway migration against a real MySQL container.
- Repository CRUD and constraint checks.
- Global exception handling and unknown‑field rejection.

## OpenAPI UI
Visit `http://localhost:8081/swagger-ui.html` to explore the generated API documentation.

## Health Endpoint
`GET /actuator/health` returns the application health status.

## Notes
- The **JWT secret** must be at least 32 bytes; the application will fail to start if missing in non‑dev profiles.
- No phone‑number fields exist anywhere in the schema or DTOs (see Phase reports).

---
*Generated on 2026‑10‑31.*