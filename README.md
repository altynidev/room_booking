# Meeting Room Booking Service

A REST API for booking meeting rooms. Users register, log in with JWT, browse rooms,
check daily availability and book time slots. The service guarantees that no two
**ACTIVE** bookings of the same room overlap: this is checked in the service layer and
enforced by a PostgreSQL exclusion constraint, so concurrent requests are also safe.
Administrators manage the room catalogue and can cancel any booking.

## Tech stack

- Java 21, Spring Boot 3.5, Maven
- Spring Web, Spring Data JPA (Hibernate), Bean Validation
- Spring Security with stateless JWT (jjwt), BCrypt password hashing
- PostgreSQL 16, Flyway migrations
- Lombok
- JUnit 5, MockMvc, Testcontainers
- Docker (multi-stage build) and Docker Compose

## Project structure

```
src/main/java/com/example/roombooking
├── controller   REST controllers
├── service      business logic
├── repository   Spring Data JPA repositories
├── dto          request/response records (passwords are never returned)
├── entity       JPA entities and enums
├── security     JWT filter/service, security config, 401/403 handlers
└── exception    custom exceptions + @RestControllerAdvice
src/main/resources/db/migration   Flyway scripts (schema + seed admin)
```

## Running with Docker

Requirements: Docker with Compose v2.

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`, and PostgreSQL at `localhost:5432`.
Flyway creates the schema and a default administrator on first start:

| username | password   | role  |
|----------|------------|-------|
| `admin`  | `admin123` | ADMIN |

> Change the admin password and set your own `JWT_SECRET` before any real deployment.

Stop the stack with `docker compose down` (add `-v` to also delete the database volume).

### Running locally without Docker for the app

Start only the database and run the app with Maven:

```bash
docker compose up -d postgres
mvn spring-boot:run
```

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
  The availability endpoint's day boundaries are computed in UTC.

## Endpoints

All endpoints except register/login require `Authorization: Bearer <token>`.

| Method | Path                                         | Access        | Description                                   | Success |
|--------|----------------------------------------------|---------------|-----------------------------------------------|---------|
| POST   | `/api/auth/register`                         | Public        | Register a new user (role USER)               | 201     |
| POST   | `/api/auth/login`                            | Public        | Log in, returns a JWT                         | 200     |
| GET    | `/api/rooms`                                 | Authenticated | List all rooms                                | 200     |
| GET    | `/api/rooms/{id}`                            | Authenticated | Get a room                                    | 200     |
| POST   | `/api/rooms`                                 | ADMIN         | Create a room                                 | 201     |
| PUT    | `/api/rooms/{id}`                            | ADMIN         | Update a room                                 | 200     |
| DELETE | `/api/rooms/{id}`                            | ADMIN         | Delete a room (only if it has no bookings)    | 204     |
| GET    | `/api/rooms/{id}/availability?date=YYYY-MM-DD` | Authenticated | Booked and free slots for a day (UTC)       | 200     |
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
