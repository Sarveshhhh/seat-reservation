package com.example.seatreservation.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ReservationNotFoundException extends DomainException {

    public ReservationNotFoundException(UUID reservationId) {
        super(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation with ID '" + reservationId + "' was not found.");
    }
}
