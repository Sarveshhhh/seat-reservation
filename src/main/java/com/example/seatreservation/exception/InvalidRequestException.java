package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
