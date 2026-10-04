package com.example.seatreservation.dto;

import com.example.seatreservation.entity.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ReservationResponse {

    @JsonProperty("reservation_id")
    private UUID reservationId;

    @JsonProperty("show_id")
    private UUID showId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("seats")
    private List<String> seats;

    @JsonProperty("amount_paise")
    private long amountPaise;

    @JsonProperty("status")
    private ReservationStatus status;

    public ReservationResponse() {
    }

    public ReservationResponse(UUID reservationId, UUID showId, String userId, List<String> seats, long amountPaise, ReservationStatus status) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.amountPaise = amountPaise;
        this.status = status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
