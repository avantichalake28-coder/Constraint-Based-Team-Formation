# Skill Match — Student Team Formation

## Quick start

* Backend setup, environment variables, Mailpit and run/test commands: [`backend/README.md`](backend/README.md)
* Authentication frontend setup, backend integration and verification: [`frontend/README.md`](frontend/README.md)
* Architecture: [`docs/architecture.md`](docs/architecture.md)
* Database design: [`docs/database-design.md`](docs/database-design.md)
* Decisions and assumptions: [`docs/decision-log.md`](docs/decision-log.md)

The React/TypeScript frontend lives in `frontend/`; the Spring Boot API and
database live in `backend/`.

## Current scope

Task 2.5 Part A hardens registration/verification mail handling and account seeding. Engine 1 adds the student profile and skill catalogue, mentor-owned project lifecycle, project roster, ranked teammate preferences and the single-source preference lock. Task 2.6 Engine 2 adds versioned project constraints, a pure team-size planner and a read-only feasibility report, plus the student classmates projection. Constraint scoring, team formation, validation and finalization remain unimplemented.

## Task 2.6 endpoints

| Method | Path | Access |
| --- | --- | --- |
| `GET` | `/api/v1/projects/{id}/classmates?page=&size=` | Rostered student on a non-draft project |
| `GET` | `/api/v1/projects/{id}/constraints` | Owning mentor |
| `PUT` | `/api/v1/projects/{id}/constraints` | Owning mentor, before the project is formed and while no formation job is active |
| `POST` | `/api/v1/projects/{id}/feasibility` | Owning mentor, read-only |

```powershell
# Classmates (student token)
curl.exe "http://localhost:8081/api/v1/projects/1/classmates?page=0&size=20" `
  -H "Authorization: Bearer $STUDENT_TOKEN"

# Read the current constraints (owning mentor token)
curl.exe http://localhost:8081/api/v1/projects/1/constraints `
  -H "Authorization: Bearer $MENTOR_TOKEN"

# Replace the whole configuration with a new version (owning mentor token)
curl.exe -X PUT http://localhost:8081/api/v1/projects/1/constraints `
  -H "Authorization: Bearer $MENTOR_TOKEN" -H "Content-Type: application/json" `
  -d '{"prohibitedPairs":[{"studentId1":7,"studentId2":9}],"mandatorySkills":[{"skillId":1,"minimumProficiency":4}]}'

# Check feasibility without writing anything (owning mentor token)
curl.exe -X POST http://localhost:8081/api/v1/projects/1/feasibility `
  -H "Authorization: Bearer $MENTOR_TOKEN"
```

## Verify

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

The suite requires Docker and runs against a real MySQL 8.0 container. It contains no skipped tests.
