package com.example.seatreservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ReservationMetrics {

    private final Counter confirmedCounter;
    private final Counter seatTakenCounter;
    private final Counter userLimitCounter;
    private final Counter idempotentReplayCounter;
    private final Counter idempotencyConflictCounter;
    private final Counter invalidRequestCounter;

    public ReservationMetrics(MeterRegistry meterRegistry) {
        this.confirmedCounter = Counter.builder("reservations_confirmed_total")
                .description("Total number of confirmed reservations")
                .register(meterRegistry);

        this.seatTakenCounter = Counter.builder("reservations_declined_total")
                .tag("reason", "seat_taken")
                .description("Total number of declined reservations due to seat already taken")
                .register(meterRegistry);

        this.userLimitCounter = Counter.builder("reservations_declined_total")
                .tag("reason", "per_user_limit")
                .description("Total number of declined reservations due to per-user limit")
                .register(meterRegistry);

        this.idempotentReplayCounter = Counter.builder("reservations_declined_total")
                .tag("reason", "idempotent_replay")
                .description("Total number of idempotent replay reservations")
                .register(meterRegistry);

        this.idempotencyConflictCounter = Counter.builder("reservations_declined_total")
                .tag("reason", "idempotency_conflict")
                .description("Total number of declined reservations due to idempotency key mismatch")
                .register(meterRegistry);

        this.invalidRequestCounter = Counter.builder("reservations_declined_total")
                .tag("reason", "invalid_request")
                .description("Total number of declined reservations due to invalid payload parameters")
                .register(meterRegistry);
    }

    public void incrementConfirmed() {
        confirmedCounter.increment();
    }

    public void incrementSeatTaken() {
        seatTakenCounter.increment();
    }

    public void incrementPerUserLimit() {
        userLimitCounter.increment();
    }

    public void incrementIdempotentReplay() {
        idempotentReplayCounter.increment();
    }

    public void incrementIdempotencyConflict() {
        idempotencyConflictCounter.increment();
    }

    public void incrementInvalidRequest() {
        invalidRequestCounter.increment();
    }
}
