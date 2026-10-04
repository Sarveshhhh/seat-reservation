# High-Throughput Seat Reservation Service

Production-grade JSON HTTP API built in Java 21 and Spring Boot to manage assigned seat reservations at scale. The service guarantees zero double-bookings, strict per-user seat limits, multi-seat all-or-nothing atomicity, and database-level idempotency under heavy concurrent traffic bursts.

---

## 1. Architecture Overview

Simple, production-ready layered architecture:

```
com.example.seatreservation
├── config        (Web, Jackson, Metric configurations)
├── controller    (REST API Controllers, X-User-Id authentication extraction)
├── dto           (Request/Response DTOs, Error responses)
├── entity        (Show, Seat, Reservation JPA entities)
├── exception     (Custom domain exceptions & @RestControllerAdvice)
├── metrics       (Custom Micrometer Prometheus metric counters)
├── repository    (Spring Data JPA Repositories with custom pessimistic queries)
├── security      (ThreadLocal SecurityContext populated by UserAuthenticationInterceptor)
└── service       (ReservationService & ShowService with explicit @Transactional boundaries)
```

---

## 2. Technology Choices

- **Java 21**: Modern LTS version with virtual threads capability and pattern matching.
- **Spring Boot 3.3.4**: Core framework for Web, JPA, Actuator, and Validation.
- **PostgreSQL**: Relational database acting as the single source of truth for row locking, unique constraints, and transaction isolation.
- **Flyway**: Versioned database migration tool.
- **Swagger / OpenAPI 3**: Interactive API documentation and specification UI (`/swagger-ui.html`).
- **Micrometer + Prometheus**: Real-time application and business metric collector.
- **Testcontainers & JUnit 5**: Containerized PostgreSQL integration test environment.

---

## 3. Concurrency & Locking Strategy

### Double-Booking Protection
When reserving seats, the transaction executes `SELECT ... FROM seats WHERE show_id = :showId AND seat_number IN (:seats) ORDER BY seat_number ASC FOR UPDATE`. This acquires database row locks on the target seats.
- **Deterministic Lock Ordering**: Seats are sorted alphabetically before locking (`ORDER BY seat_number ASC`). This eliminates circular wait deadlocks when concurrent users request overlapping seats.

### Per-User Seat Limit Enforcement
A single user cannot exceed 4 confirmed seats per show.
- Under concurrency, simple `SELECT COUNT(*)` suffers from race conditions. We execute `SELECT pg_advisory_xact_lock(hashtext('show_id:user_id'))` to acquire an in-memory transactional advisory lock in PostgreSQL for the specific user-show scope. This serializes limit verification cleanly per user without blocking other users.

### Multi-Seat Atomicity (All-or-Nothing)
If a request asks for `["A12", "A13"]` and `A12` is already confirmed, the service throws `SeatAlreadyTakenException` (409 Conflict), rolling back the transaction. Seat `A13` remains `AVAILABLE`.

---

## 4. Idempotency Strategy

- Every reservation request includes an `idempotency_key`.
- The service computes a deterministic SHA-256 hash of `showId + sortedSeats`.
- **Pre-check**: If the key exists for the user, matching hashes return the original reservation response (`200 OK` / `201 Created`). Mis-matched payloads throw `409 Conflict`.
- **Database Safety**: The database enforces `CONSTRAINT uq_user_idempotency UNIQUE (user_id, idempotency_key)`. If simultaneous identical requests land at the exact same millisecond, PostgreSQL rejects the second insertion with a unique constraint violation. The service catches `DataIntegrityViolationException`, re-queries the database, and returns the existing reservation safely.

---

## 5. API Endpoints & Example Curl Commands

### 1. Create Show
`POST /shows`
```bash
curl -X POST http://localhost:8080/shows \
  -H "Content-Type: application/json" \
  -d '{
    "name": "friday-night",
    "seats": ["A1", "A2", "A3", "A4"],
    "price_paise": 25000
  }'
```

### 2. Reserve Seats
`POST /shows/{showId}/reserve`
```bash
curl -X POST http://localhost:8080/shows/c1f7b80a-9d2c-49e0-8114-1e05d045d4e1/reserve \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -d '{
    "seats": ["A1", "A2"],
    "idempotency_key": "unique-request-key-001"
  }'
```

### 3. Cancel Reservation
`POST /reservations/{reservationId}/cancel`
```bash
curl -X POST http://localhost:8080/reservations/8d3e91a0-12ab-4c3d-8f9e-0123456789ab/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123"
```

### 4. Query Show State
`GET /shows/{showId}`
```bash
curl -X GET http://localhost:8080/shows/c1f7b80a-9d2c-49e0-8114-1e05d045d4e1
```

### 5. Interactive Swagger Documentation
`GET /swagger-ui.html`
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 6. Observability & Health Probes

- **Process Liveness**: `GET /health/live` (Returns `200 OK` `{"status":"UP"}`)
- **Database Readiness**: `GET /health/ready` (Executes DB check `SELECT 1`. Fails closed with `503 Service Unavailable` if PostgreSQL is down).
- **Prometheus Metrics**: `GET /actuator/prometheus`
  - `reservations_confirmed_total`
  - `reservations_declined_total{reason="seat_taken"}`
  - `reservations_declined_total{reason="per_user_limit"}`
  - `reservations_declined_total{reason="idempotent_replay"}`
  - `reservations_declined_total{reason="idempotency_conflict"}`

---

## 7. Local & Docker Setup

### Option A: Local Development with PostgreSQL
Run with environment variables:
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/seatreservation"
export DATABASE_USERNAME="postgres"
export DATABASE_PASSWORD="postgres"
mvn spring-boot:run
```

### Option B: Docker Compose
```bash
# Docker Compose V2 (Recommended)
docker compose up --build

# Docker Compose V1 (Legacy)
docker-compose up --build
```

### Executing Concurrency Burst Test
```bash
./burst.sh http://localhost:8080
```

---

## 8. Known Tradeoffs

- **Explicit Cancellation vs Auto-Expiry**: We implemented explicit cancellation (`AVAILABLE <-> CONFIRMED`). For temporary holds (e.g. 5-minute checkout windows), a background cleanup task or Redis TTL would be introduced.
- **Advisory Locks in PostgreSQL**: `pg_advisory_xact_lock` is PostgreSQL-specific. It provides optimal performance for per-user locking without requiring additional tables.
