# SkillMatch frontend

React, TypeScript, Vite, Tailwind CSS, React Router, Zustand, TanStack Query,
Axios, React Hook Form, Zod, Framer Motion and Lucide React student application.

## Run locally

```powershell
cd frontend
npm install
Copy-Item .env.example .env.local
npm run dev
```

Set `VITE_API_BASE_URL` when the backend is not available at
`http://localhost:8081`. Set `VITE_ALLOWED_EMAIL_DOMAIN` to the same value as
the backend's `ALLOWED_EMAIL_DOMAIN` so registration form validation matches
the server policy. The backend must also be configured with `JWT_SECRET` and
its mail settings for registration and verification. See
[the backend setup](../backend/README.md).

## Authentication and session

The UI integrates the existing Spring endpoints only:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Email/password sign-in; returns a 60-minute bearer token |
| `POST` | `/api/v1/auth/register` | Student account creation and verification email |
| `GET` | `/api/v1/auth/me` | Resolve the current bearer-token user |
| `GET` | `/api/v1/auth/verify-email?token=...` | Email verification |
| `POST` | `/api/v1/auth/resend-verification` | Resend an email verification link |

Tokens are kept in memory and, when “Remember me” is selected, in tab-scoped
`sessionStorage` until their backend-provided expiration. Passwords are never
stored. A protected page
validates its session with `/api/v1/auth/me`; an expired or invalid token is
cleared and the student is returned to login. Logout clears the local token.
The backend has no refresh-token, server logout, password-reset, Google, or
GitHub authentication endpoints; those options are not represented as working
integrations.

## Student application API

The authenticated workspace uses these existing backend operations:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`, `PUT` | `/api/v1/students/me` | Read and update name, department, semester and interests |
| `GET`, `PUT` | `/api/v1/students/me/skills` | Read and replace skills, including proficiency from 1–5 |
| `GET` | `/api/v1/skills` | Read the shared skill catalogue |
| `GET` | `/api/v1/projects` | Read projects whose roster includes the current student |
| `GET` | `/api/v1/projects/{id}/classmates` | Read rostered classmates |
| `GET`, `PUT` | `/api/v1/projects/{id}/preferences/me` and `/api/v1/projects/{id}/preferences` | Read and save up to two ranked teammate preferences |

Email and SRN are read-only profile identifiers. Skills can be selected only
from the shared catalogue; mentors/admins manage that catalogue. Interest text
is stored on the profile. The profile API treats a null semester as “no change,”
so the UI does not offer a semester-clear operation.

### Backend limitations shown in the UI

The frontend does not simulate unavailable features:

* Formation jobs and candidate-team results are mentor-only endpoints. There is
  no student request/status or assigned-team endpoint, so students cannot start
  formation or view team members/compatibility from this API.
* There is no notification API for listing, unread status, or marking items as
  read.
* Project preferences support only ranked teammate choices, not category or
  role preferences. The UI documents these limits on the relevant pages.

## Design and assets

The default design is Aurora Light. Aurora and Glass Horizon each support light
and dark modes, and appearance is persisted separately from authentication.
The existing local authentication artwork is retained. The protected `/app`
workspace has one responsive sidebar and routes for Dashboard, My Profile,
Skills & Interests, Project Preferences, Team Formation, My Team, Notifications,
and Settings.

Avatar onboarding and customization are paused: login opens the dashboard
directly, no protected route requires an avatar, and avatar assets/components
remain isolated for possible reintroduction. See the
[avatar inventory](src/features/students/avatar/README.md).

## Verify

```powershell
npm test
npm run build
```
