package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

public class SeatAlreadyTakenException extends DomainException {

    public SeatAlreadyTakenException(String message) {
        super(HttpStatus.CONFLICT, "SEAT_ALREADY_TAKEN", message);
    }
}
