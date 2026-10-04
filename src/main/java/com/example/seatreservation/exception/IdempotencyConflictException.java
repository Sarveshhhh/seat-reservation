package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends DomainException {

    public IdempotencyConflictException(String idempotencyKey) {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", 
                "Idempotency key '" + idempotencyKey + "' was reused with a different request body.");
    }
}
