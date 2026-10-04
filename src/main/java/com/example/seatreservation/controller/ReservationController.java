package com.example.seatreservation.controller;

import com.example.seatreservation.dto.CancelReservationResponse;
import com.example.seatreservation.dto.ReservationResponse;
import com.example.seatreservation.dto.ReserveSeatsRequest;
import com.example.seatreservation.security.SecurityContext;
import com.example.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<ReservationResponse> reserveSeats(
            @PathVariable("showId") UUID showId,
            @Valid @RequestBody ReserveSeatsRequest request
    ) {
        String userId = SecurityContext.getCurrentUserId();
        ReservationResponse response = reservationService.reserveSeats(showId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ResponseEntity<CancelReservationResponse> cancelReservation(
            @PathVariable("reservationId") UUID reservationId
    ) {
        String userId = SecurityContext.getCurrentUserId();
        CancelReservationResponse response = reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok(response);
    }
}
