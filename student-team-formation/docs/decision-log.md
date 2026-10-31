# Decision Log

| ID | Decision | Alternatives | Reason | Status | Date |
|----|----------|--------------|--------|--------|------|
| D001 | Spring Boot version 4.1.2 | 4.0.x, 3.2.x | Latest stable GA, compatible with required libraries (Flyway 11+, Spring Security 7.1, springdoc 3.x) | DECIDED | 2026-10-31 |
| D002 | Use Maven as build system | Gradle, Bazel | Conventional for Spring Boot, easier for Testcontainers integration | DECIDED | 2026-10-31 |
| D003 | Java version 17 | 21, 11 | LTS, supported by Spring Boot 4.1 | DECIDED | 2026-10-31 |
| A001 | Use H2 for any test database | None | **ASSUMPTION**: will use real MySQL via Testcontainers as per rule 4; no in‑memory DBs.
| A002 | Directory layout `student-team-formation/backend` | `backend` only | **ASSUMPTION**: follows prompt to create new folder.
