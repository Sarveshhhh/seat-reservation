package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

public class PerUserLimitExceededException extends DomainException {

    public PerUserLimitExceededException(int limit, long attemptedTotal) {
        super(HttpStatus.CONFLICT, "PER_USER_LIMIT_EXCEEDED", 
                "Requested seat count exceeds per-user show limit of " + limit + " (Attempted total: " + attemptedTotal + ")");
    }
}
