# Meeting Room Booking Service

A web application and REST API for booking meeting rooms. Users register, log in with JWT,
browse rooms, check daily availability and book time slots. The service guarantees that no two
**ACTIVE** bookings of the same room overlap: this is checked in the service layer and
enforced by a PostgreSQL exclusion constraint, so concurrent requests are also safe.
Administrators manage the room catalogue and can cancel any booking.

## Tech stack

- Java 21, Spring Boot 3.5, Maven
- Spring Web, Spring Data JPA (Hibernate), Bean Validation
- Spring Security with stateless JWT (jjwt), BCrypt password hashing
- PostgreSQL 16, Flyway migrations
- springdoc-openapi (Swagger UI), Spring Boot Actuator, Micrometer/Prometheus, ECS JSON logs
- Lombok
- JUnit 5, MockMvc, Testcontainers
- Frontend: React 19, TypeScript, Vite, React Router; served by nginx
- Docker (multi-stage builds), Docker Compose, GitHub Actions

## Architecture

```
browser ──► nginx :3000 ──┬── /            static React app (SPA)
                          └── /api, /swagger-ui, /v3/api-docs ──► Spring Boot :8080 ──► PostgreSQL :5432
                                                                  actuator :8081 (internal only)
```

The browser only ever talks to nginx, so the website and the API share one origin and no
CORS configuration is needed. The Vite dev server proxies `/api` the same way.

## Project structure

```
src/main/java/com/example/roombooking
├── config       OpenAPI definition
├── controller   REST controllers
├── service      business logic
├── repository   Spring Data JPA repositories
├── dto          request/response records (passwords are never returned)
├── entity       JPA entities and enums
├── security     JWT filter/service, security config, 401/403 handlers
└── exception    custom exceptions + @RestControllerAdvice
src/main/resources/db/migration   Flyway scripts (schema + seed admin)
frontend/
├── src/api.ts         typed API client
├── src/auth.tsx       login state (token in localStorage, user from /api/auth/me)
├── src/pages          Rooms, RoomDetail, MyBookings, AdminRooms, Login, Register
├── src/components     Layout, DayTimeline
├── nginx.conf         SPA fallback + reverse proxy to the API
└── Dockerfile         node build → nginx runtime
.github/workflows/ci.yml   CI pipeline
```

## Running with Docker

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

| What                 | URL                                     |
|----------------------|-----------------------------------------|
| Website              | http://localhost:3000                   |
| Swagger UI           | http://localhost:3000/swagger-ui.html   |
| API (direct)         | http://localhost:8080                   |
| PostgreSQL           | `localhost:5432`                        |

Flyway creates the schema and a default administrator on first start:

| username | password   | role  |
|----------|------------|-------|
| `admin`  | `admin123` | ADMIN |

> These are development defaults. Before anyone else can reach the app, set `ADMIN_PASSWORD`
> (it replaces `admin123` on startup) and your own `JWT_SECRET`. See [Deployment](#deployment).

Stop the stack with `docker compose down` (add `-v` to also delete the database volume).

### Running locally without Docker for the app

Start only the database and run the app with Maven:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

### Frontend development

With the API running on `:8080` (either way above), start Vite with hot reload:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api to :8080 (override with API_URL)
npm run build      # type check + production build into dist/
```

## The website

- **Rooms**: all rooms as cards, with search by name and a minimum-seats filter.
- **Room page**: pick a day; a 24-hour timeline shows booked time, the elapsed part of
  today and your pending selection. Click a free slot to prefill the form, adjust the
  times in 15-minute steps and book. Conflicts and validation errors from the API show inline.
- **My bookings**: upcoming, past and cancelled tabs; cancel upcoming bookings.
- **Manage rooms** (ADMIN only): create, edit and delete rooms.

All times are shown in the browser's time zone. The room page asks the API for availability
with `zone=<browser zone>`, so "a day" means the user's local day.

## Observability

| Endpoint (port 8081)                    | Purpose                                  |
|-----------------------------------------|------------------------------------------|
| `/actuator/health`                      | Overall health (includes the database)   |
| `/actuator/health/liveness`, `/readiness` | Kubernetes-style probes                |
| `/actuator/info`                        | Build info (artifact, version, time)     |
| `/actuator/prometheus`                  | Metrics in Prometheus format             |

Actuator runs on a separate management port that docker-compose does **not** publish; the
compose healthcheck uses it from inside the container. To scrape metrics, put Prometheus on the
same Docker network, or temporarily add `"8081:8081"` to the `app` ports.

In Docker, logs are one JSON object per line in Elastic Common Schema
(`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`). Running with Maven keeps the readable console format.

API documentation is generated from the code: Swagger UI at `/swagger-ui.html`, the raw
OpenAPI document at `/v3/api-docs`. Use **Authorize** with a token from `POST /api/auth/login`.

## Deployment

- **Quick public link from your PC**: a Cloudflare quick tunnel, for demos.
- **Production server**: `deploy/docker-compose.prod.yml` runs the CI-published images behind
  Caddy with automatic HTTPS. Only ports 80/443 are exposed, and every secret is required.

Step-by-step instructions, updates, rollbacks and backups: [deploy/DEPLOYMENT.md](deploy/DEPLOYMENT.md).

## CI/CD

`.github/workflows/ci.yml` runs on every push and pull request:

1. **Backend tests**: `mvn verify` (Testcontainers on the runner's Docker).
2. **Frontend build**: `npm ci` and `npm run build` (TypeScript type check included).
3. **End-to-end**: `docker compose up --wait`, then `scripts/smoke-test.sh` against
   `http://localhost:3000`, so requests go through nginx exactly as the browser's do.
4. **Publish images** (pushes to `main` only): `ghcr.io/<owner>/<repo>-api` and `-web`,
   tagged `latest` and `sha-<commit>`.

Dependabot (`.github/dependabot.yml`) opens weekly update PRs for Maven, npm and Actions.

### Tests

```bash
mvn verify
```

The integration tests start a throwaway PostgreSQL container with Testcontainers,
so Docker must be running.

### Smoke test

`scripts/smoke-test.sh` exercises every endpoint of a running instance with curl
(about 50 checks: success paths, validation errors, 401/403/404/409 cases, the error
body format and HS256 token signing) and exits non-zero if any check fails.

```bash
docker compose up -d --build
./scripts/smoke-test.sh          # on Windows: run from Git Bash or WSL
docker compose down -v           # stop and delete test data
```

It needs `bash`, `curl` and `sed`. It creates uniquely named users and rooms, so it can
be rerun against the same database. Override the target with `BASE_URL`, and the admin
credentials with `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

## Configuration

All settings come from environment variables and have development defaults:

| Variable            | Default                                            | Description                                   |
|---------------------|----------------------------------------------------|-----------------------------------------------|
| `DB_URL`            | `jdbc:postgresql://localhost:5432/roombooking`     | JDBC URL                                      |
| `DB_USERNAME`       | `postgres`                                         | Database user                                 |
| `DB_PASSWORD`       | `postgres`                                         | Database password                             |
| `JWT_SECRET`        | dev-only key (see `application.yml`)               | Base64-encoded key, ≥ 256 bits; tokens are signed with HS256 |
| `JWT_EXPIRATION_MS` | `3600000`                                          | Token lifetime (ms)                           |
| `SERVER_PORT`       | `8080`                                             | HTTP port                                     |
| `MANAGEMENT_PORT`   | `8081`                                             | Actuator port (health, info, metrics)         |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | unset (plain text); `ecs` in Docker | Structured log format                 |
| `WEB_PORT` (compose) | `3000`                                            | Host port of the website                      |
| `ADMIN_PASSWORD`    | unset (seed `admin123` kept)                       | Password for `admin`, applied on every start (min. 12 chars) |
| `ADMIN_USERNAME`    | `admin`                                            | Administrator account managed by `ADMIN_PASSWORD` |
| `DB_PORT` (compose)  | `5432`                                            | Host port of PostgreSQL (change if a local Postgres already uses 5432) |

Generate a secret with, for example, `openssl rand -base64 32`.

## Business rules

- A booking may not overlap another **ACTIVE** booking of the same room → `409 Conflict`.
  Intervals are half-open, so back-to-back bookings (09:00–10:00 and 10:00–11:00) are allowed.
- `startTime` must be before `endTime` and in the future → `400 Bad Request`.
- Users can cancel only their own bookings (`403` otherwise); ADMIN can cancel any.
  Cancelling an already cancelled booking → `409`.
- Only ADMIN can create, update or delete rooms. A room that has bookings cannot be deleted → `409`.
- Registration always creates a `USER`; roles cannot be self-assigned.
- All times are ISO-8601 with offset (e.g. `2026-10-01T09:00:00Z`) and are returned in UTC.
  The availability endpoint's day boundaries are computed in UTC, or in the IANA time zone
  given by the optional `zone` parameter (e.g. `zone=Asia/Almaty`); slots are still returned in UTC.

## Endpoints

All endpoints except register/login (and the Swagger/OpenAPI docs) require `Authorization: Bearer <token>`.

| Method | Path                                         | Access        | Description                                   | Success |
|--------|----------------------------------------------|---------------|-----------------------------------------------|---------|
| POST   | `/api/auth/register`                         | Public        | Register a new user (role USER)               | 201     |
| POST   | `/api/auth/login`                            | Public        | Log in, returns a JWT                         | 200     |
| GET    | `/api/auth/me`                               | Authenticated | The current user (id, username, role)         | 200     |
| GET    | `/api/rooms`                                 | Authenticated | List all rooms                                | 200     |
| GET    | `/api/rooms/{id}`                            | Authenticated | Get a room                                    | 200     |
| POST   | `/api/rooms`                                 | ADMIN         | Create a room                                 | 201     |
| PUT    | `/api/rooms/{id}`                            | ADMIN         | Update a room                                 | 200     |
| DELETE | `/api/rooms/{id}`                            | ADMIN         | Delete a room (only if it has no bookings)    | 204     |
| GET    | `/api/rooms/{id}/availability?date=YYYY-MM-DD[&zone=Area/City]` | Authenticated | Booked and free slots for a day (UTC by default) | 200 |
| POST   | `/api/bookings`                              | Authenticated | Book a room                                   | 201     |
| GET    | `/api/bookings/my`                           | Authenticated | Current user's bookings                       | 200     |
| PATCH  | `/api/bookings/{id}/cancel`                  | Owner / ADMIN | Cancel a booking                              | 200     |

### Error format

Every error uses the same JSON body:

```json
{
  "timestamp": "2026-09-25T06:29:46.916Z",
  "status": 409,
  "error": "Conflict",
  "message": "Room is already booked for the requested time"
}
```

| Status | When                                                                 |
|--------|----------------------------------------------------------------------|
| 400    | Validation errors, malformed JSON, invalid time range, bad parameters |
| 401    | Missing/invalid/expired token, wrong credentials                     |
| 403    | Authenticated but not allowed (non-admin room changes, others' bookings) |
| 404    | Room or booking not found                                            |
| 409    | Overlapping booking, duplicate username/room name, already cancelled, room has bookings |

## Example requests

```bash
BASE=http://localhost:8080

# Log in as the seeded admin and keep the token
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r .token)

# Create a room (ADMIN)
curl -X POST $BASE/api/rooms \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Orion","capacity":8,"floor":2}'

# Update / delete a room (ADMIN)
curl -X PUT $BASE/api/rooms/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Orion XL","capacity":12,"floor":2}'
curl -X DELETE $BASE/api/rooms/1 -H "Authorization: Bearer $ADMIN_TOKEN"

# Register and log in as a regular user
curl -X POST $BASE/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}'

TOKEN=$(curl -s -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' | jq -r .token)

# List rooms
curl $BASE/api/rooms -H "Authorization: Bearer $TOKEN"

# Book a room
curl -X POST $BASE/api/bookings \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"roomId":1,"startTime":"2026-10-01T09:00:00Z","endTime":"2026-10-01T10:00:00Z"}'

# Check availability for a day
curl "$BASE/api/rooms/1/availability?date=2026-10-01" -H "Authorization: Bearer $TOKEN"

# My bookings
curl $BASE/api/bookings/my -H "Authorization: Bearer $TOKEN"

# Cancel a booking
curl -X PATCH $BASE/api/bookings/1/cancel -H "Authorization: Bearer $TOKEN"
```

Example availability response:

```json
{
  "roomId": 1,
  "roomName": "Orion",
  "date": "2026-10-01",
  "bookedSlots": [
    { "start": "2026-10-01T09:00:00Z", "end": "2026-10-01T10:00:00Z" }
  ],
  "freeSlots": [
    { "start": "2026-10-01T00:00:00Z", "end": "2026-10-01T09:00:00Z" },
    { "start": "2026-10-01T10:00:00Z", "end": "2026-10-02T00:00:00Z" }
  ]
}
```
