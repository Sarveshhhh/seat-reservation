package com.example.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ShowResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("price_paise")
    private long pricePaise;

    @JsonProperty("total_seats")
    private int totalSeats;

    @JsonProperty("available_count")
    private long availableCount;

    @JsonProperty("held_count")
    private long heldCount = 0;

    @JsonProperty("confirmed_count")
    private long confirmedCount;

    @JsonProperty("seats")
    private List<SeatDto> seats;

    public ShowResponse() {
    }

    public ShowResponse(UUID id, String name, long pricePaise, int totalSeats, long availableCount, long confirmedCount, List<SeatDto> seats) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.totalSeats = totalSeats;
        this.availableCount = availableCount;
        this.heldCount = 0;
        this.confirmedCount = confirmedCount;
        this.seats = seats;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public long getAvailableCount() {
        return availableCount;
    }

    public void setAvailableCount(long availableCount) {
        this.availableCount = availableCount;
    }

    public long getHeldCount() {
        return heldCount;
    }

    public void setHeldCount(long heldCount) {
        this.heldCount = heldCount;
    }

    public long getConfirmedCount() {
        return confirmedCount;
    }

    public void setConfirmedCount(long confirmedCount) {
        this.confirmedCount = confirmedCount;
    }

    public List<SeatDto> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatDto> seats) {
        this.seats = seats;
    }
}
