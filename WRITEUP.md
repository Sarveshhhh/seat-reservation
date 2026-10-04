# Technical Architecture & Concurrency Deep-Dive (WRITEUP.md)

This document explains the core engineering decisions, concurrency defenses, and database mechanisms used in the Paytm Seat Reservation Service.

---

## 1. Atomic Decision

**Where does the atomic decision happen?**
The atomic reservation decision occurs inside the PostgreSQL database engine at the row level during the transaction execution.

**Exact PostgreSQL Mechanism:**
Pessimistic Row-Level Locking (`SELECT ... FROM seats ... FOR UPDATE`) combined with database constraints.

**Why a Read-Then-Write Approach is Unsafe:**
In standard `READ COMMITTED` isolation, executing a `SELECT status FROM seats WHERE seat_number = 'A1'` in Java followed by an `UPDATE seats SET status = 'CONFIRMED'` introduces a race condition. Two concurrent threads can both execute the `SELECT` query at the exact same millisecond, read `status = 'AVAILABLE'`, and proceed to execute the `UPDATE`. Both updates succeed, resulting in a **double-booking bug**. `SELECT ... FOR UPDATE` acquires an exclusive lock on the row, forcing subsequent transactions to block until the lock-holding transaction commits or rolls back.

---

## 2. Multi-Seat Concurrency & Deadlock Prevention

**Why Seats are Sorted Before Locking:**
If User 1 requests seats `["A1", "A2"]` and User 2 concurrently requests seats `["A2", "A1"]`, without lock ordering:
- Thread 1 locks `A1` and waits for `A2`.
- Thread 2 locks `A2` and waits for `A1`.

This creates a circular dependency deadlock, causing PostgreSQL to abort one of the transactions with a 500 server error.

**How Sorting Solves Deadlocks:**
By sorting seat numbers alphabetically (`ORDER BY seat_number ASC`) prior to lock acquisition, both Thread 1 and Thread 2 attempt to lock `A1` first. The thread that wins `A1` proceeds to lock `A2`, while the other thread waits cleanly on `A1`. Deadlocks are completely eliminated.

---

## 3. Idempotency Design

- **Storage**: Idempotency keys are stored directly in the `reservations` table alongside a SHA-256 hash of the request payload (`request_hash`).
- **Database Constraint**: `CONSTRAINT uq_user_idempotency UNIQUE (user_id, idempotency_key)`.
- **Request Hash Validation**: If a key is presented again for the same user, the service compares `request_hash`. If identical, it replays the existing reservation (`200 OK` / `201 Created`). If different, it throws `409 Conflict`.
- **Simultaneous Duplicate Requests**: If two identical requests with key `abc` land simultaneously, both pass the initial read check. The second thread to execute `INSERT` hits `DataIntegrityViolationException`. The service catches this exception, queries the newly committed reservation, verifies the request hash, and returns the response without crashing.

---

## 4. Per-User Limit Guarantee

**How Concurrent Requests Cannot Bypass the Limit:**
Checking user reservation count with `SELECT COUNT(*)` followed by `INSERT` is susceptible to race conditions under concurrent calls. 

We use **PostgreSQL Advisory Locks** (`SELECT pg_advisory_xact_lock(hashtext('show_id:user_id'))`). This acquires a lightweight, transaction-scoped lock in Postgres memory for that specific user on that show. Concurrent requests from the same user are serialized at the transaction boundary, guaranteeing that `countConfirmedSeatsByShowIdAndUserId` accurately evaluates the user's seat count before adding new seats.

---

## 5. Cancellation Safety

- **Ownership Validation**: Before modifying state, the service verifies `reservation.getUserId().equals(authenticatedUserId)`. If mismatched, it throws `ForbiddenException` (403).
- **Seat Release**: Only the seats associated with the specific reservation ID (via `reservation_seats`) are locked and updated to `AVAILABLE`. Seats belonging to other users or reservations are never touched.
- **Idempotent Cancellation**: If a reservation is already `CANCELLED`, repeated calls return `200 OK` safely without executing redundant updates.

---

## 6. Consistency vs Availability

**PostgreSQL Failure Behavior:**
If PostgreSQL becomes unavailable, the readiness check `/health/ready` fails closed, returning `503 Service Unavailable`.

**Tradeoff Rationale:**
In financial and ticketing systems, **Consistency is strictly prioritized over Availability**. Allowing reservations to proceed during database unreachability risks double-bookings and financial reconciliation discrepancies. Returning temporary HTTP 503 errors guarantees system integrity.

---

## 7. Observability & Alerting

- **Correlation IDs**: Every request processes an `X-Request-Id` header (or generates a UUID) which is included in all log statements and error JSON responses.
- **Prometheus Metrics**:
  - `reservations_confirmed_total`
  - `reservations_declined_total{reason="..."}`
- **2 AM Page Alerts**:
  1. Spikes in `reservations_declined_total{reason="5xx"}`.
  2. Database readiness probe failures on `/health/ready`.
  3. HikariCP connection pool exhaustion timeouts.

---

## 8. AI Usage Documentation

AI tools were used as a development aid during the assignment, primarily for:
- Generating boilerplate code and DTO/repository scaffolding.
- Assisting with JUnit concurrency test setup.
- Reviewing edge cases and alternative implementation approaches.
- Structuring documentation and the README.

The final implementation and design decisions were reviewed and adapted based on the assignment requirements, particularly around PostgreSQL transactions, row-level locking, advisory locks, idempotency, and concurrency handling.

---

## 9. Next Technical Improvements

1. **JWT Authentication**: Upgrade `X-User-Id` dev header to RSA-signed JWT tokens verified via Spring Security.
2. **Reservation Expiry / Temporary Holds**: Implement TTL-backed temporary hold status (`HELD -> CONFIRMED`) using Redis or scheduled background sweeps.
3. **Connection Pool & Read Replicas**: Tune HikariCP max pool sizes and route read-only queries (`GET /shows/{showId}`) to PostgreSQL read replicas.
4. **Rate Limiting**: Implement token-bucket rate limiting (Bucket4j) per IP / User ID.
