package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ShowNotFoundException extends DomainException {

    public ShowNotFoundException(UUID showId) {
        super(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "Show with ID '" + showId + "' was not found.");
    }
}
