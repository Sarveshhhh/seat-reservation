package com.example.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class CreateShowRequest {

    @NotBlank(message = "Show name must not be blank")
    @JsonProperty("name")
    private String name;

    @NotEmpty(message = "Seats list must not be empty")
    @JsonProperty("seats")
    private List<String> seats;

    @Positive(message = "Price in paise must be positive")
    @JsonProperty("price_paise")
    private long pricePaise;

    public CreateShowRequest() {
    }

    public CreateShowRequest(String name, List<String> seats, long pricePaise) {
        this.name = name;
        this.seats = seats;
        this.pricePaise = pricePaise;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }
}
