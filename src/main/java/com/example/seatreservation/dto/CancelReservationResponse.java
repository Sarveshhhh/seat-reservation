package com.example.seatreservation.dto;

import com.example.seatreservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class CancelReservationResponse {

    @JsonProperty("reservation_id")
    private UUID reservationId;

    @JsonProperty("status")
    private ReservationStatus status;

    @JsonProperty("message")
    private String message;

    public CancelReservationResponse() {
    }

    public CancelReservationResponse(UUID reservationId, ReservationStatus status, String message) {
        this.reservationId = reservationId;
        this.status = status;
        this.message = message;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
