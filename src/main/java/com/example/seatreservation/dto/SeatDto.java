package com.example.seatreservation.dto;

import com.example.seatreservation.entity.SeatStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SeatDto {

    @JsonProperty("seat_number")
    private String seatNumber;

    @JsonProperty("status")
    private SeatStatus status;

    public SeatDto() {
    }

    public SeatDto(String seatNumber, SeatStatus status) {
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }
}
