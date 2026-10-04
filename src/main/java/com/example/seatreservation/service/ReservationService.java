package com.example.seatreservation.service;

import com.example.seatreservation.dto.ReservationResponse;
import com.example.seatreservation.dto.ReserveSeatsRequest;
import com.example.seatreservation.entity.Reservation;
import com.example.seatreservation.entity.Seat;
import com.example.seatreservation.entity.SeatStatus;
import com.example.seatreservation.entity.Show;
import com.example.seatreservation.exception.IdempotencyConflictException;
import com.example.seatreservation.exception.InvalidRequestException;
import com.example.seatreservation.exception.PerUserLimitExceededException;
import com.example.seatreservation.exception.SeatAlreadyTakenException;
import com.example.seatreservation.exception.ShowNotFoundException;
import com.example.seatreservation.repository.ReservationRepository;
import com.example.seatreservation.repository.SeatRepository;
import com.example.seatreservation.repository.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final com.example.seatreservation.metrics.ReservationMetrics reservationMetrics;
    private final int maxSeatsPerUserPerShow;

    public ReservationService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationRepository reservationRepository,
            com.example.seatreservation.metrics.ReservationMetrics reservationMetrics,
            @Value("${app.reservation.max-seats-per-user-per-show:4}") int maxSeatsPerUserPerShow
    ) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationMetrics = reservationMetrics;
        this.maxSeatsPerUserPerShow = maxSeatsPerUserPerShow;
    }

    @Transactional
    public ReservationResponse reserveSeats(UUID showId, String userId, ReserveSeatsRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidRequestException("User identity must be provided");
        }
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new InvalidRequestException("Reservation must specify at least one seat");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new InvalidRequestException("Idempotency key must not be blank");
        }

        // Check for duplicate seats in request payload
        Set<String> uniqueCheck = new HashSet<>();
        for (String seatNumber : request.getSeats()) {
            if (seatNumber == null || seatNumber.isBlank()) {
                throw new InvalidRequestException("Seat number cannot be blank");
            }
            if (!uniqueCheck.add(seatNumber.trim())) {
                throw new InvalidRequestException("Duplicate seat '" + seatNumber + "' in reservation payload");
            }
        }

        // Sort requested seats deterministically to prevent deadlocks
        List<String> sortedSeats = request.getSeats().stream()
                .map(String::trim)
                .sorted()
                .toList();

        String requestHash = computeRequestHash(showId, sortedSeats);
        String idempotencyKey = request.getIdempotencyKey().trim();

        // 1. Pre-check idempotency key
        Optional<Reservation> existingReservationOpt = reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingReservationOpt.isPresent()) {
            Reservation existing = existingReservationOpt.get();
            if (existing.getRequestHash().equals(requestHash)) {
                log.info("Idempotent replay detected for user {} with key {}", userId, idempotencyKey);
                reservationMetrics.incrementIdempotentReplay();
                return mapToReservationResponse(existing);
            } else {
                log.warn("Idempotency key conflict for user {}: key {} reused with different payload", userId, idempotencyKey);
                reservationMetrics.incrementIdempotencyConflict();
                throw new IdempotencyConflictException(idempotencyKey);
            }
        }

        // 2. Fetch Show
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        // 3. Acquire pessimistic locks on requested seats in deterministic sorted order
        List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInOrderAscForUpdate(showId, sortedSeats);

        if (lockedSeats.size() != sortedSeats.size()) {
            reservationMetrics.incrementInvalidRequest();
            throw new InvalidRequestException("One or more requested seats do not exist in show");
        }

        // 4. Verify all requested seats are AVAILABLE
        for (Seat seat : lockedSeats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                reservationMetrics.incrementSeatTaken();
                throw new SeatAlreadyTakenException("Seat '" + seat.getSeatNumber() + "' is already reserved");
            }
        }

        // 5. Acquire Postgres Advisory Lock per user per show to enforce per-user seat limit under concurrency
        String lockKey = showId + ":" + userId;
        reservationRepository.acquireAdvisoryLock(lockKey);

        long existingConfirmedCount = reservationRepository.countConfirmedSeatsByShowIdAndUserId(showId, userId);
        long newTotal = existingConfirmedCount + sortedSeats.size();
        if (newTotal > maxSeatsPerUserPerShow) {
            reservationMetrics.incrementPerUserLimit();
            throw new PerUserLimitExceededException(maxSeatsPerUserPerShow, newTotal);
        }

        // 6. Update seat statuses to CONFIRMED
        for (Seat seat : lockedSeats) {
            seat.setStatus(SeatStatus.CONFIRMED);
        }

        // 7. Persist Reservation
        long totalAmountPaise = sortedSeats.size() * show.getPricePaise();
        Reservation reservation = new Reservation(
                show,
                userId,
                totalAmountPaise,
                idempotencyKey,
                requestHash,
                lockedSeats
        );

        try {
            Reservation saved = reservationRepository.save(reservation);
            log.info("Successfully created reservation {} for user {} on show {} (Seats: {})", 
                    saved.getId(), userId, showId, sortedSeats);
            reservationMetrics.incrementConfirmed();
            return mapToReservationResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Simultaneous identical request collision handling
            log.warn("Data integrity exception during reservation save - re-checking idempotency key: {}", ex.getMessage());
            Reservation fallback = reservationRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            if (fallback.getRequestHash().equals(requestHash)) {
                reservationMetrics.incrementIdempotentReplay();
                return mapToReservationResponse(fallback);
            } else {
                reservationMetrics.incrementIdempotencyConflict();
                throw new IdempotencyConflictException(idempotencyKey);
            }
        }
    }

    @Transactional
    public com.example.seatreservation.dto.CancelReservationResponse cancelReservation(UUID reservationId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidRequestException("User identity must be provided");
        }

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new com.example.seatreservation.exception.ReservationNotFoundException(reservationId));

        if (!reservation.getUserId().equals(userId)) {
            log.warn("Unauthorized cancellation attempt: User {} tried to cancel reservation {} owned by {}", 
                    userId, reservationId, reservation.getUserId());
            throw new com.example.seatreservation.exception.ForbiddenException("You are not authorized to cancel this reservation");
        }

        if (reservation.getStatus() == com.example.seatreservation.entity.ReservationStatus.CANCELLED) {
            return new com.example.seatreservation.dto.CancelReservationResponse(reservationId, com.example.seatreservation.entity.ReservationStatus.CANCELLED, "Reservation was already cancelled");
        }

        List<Seat> seats = reservation.getSeats();
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        reservation.setStatus(com.example.seatreservation.entity.ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        log.info("Successfully cancelled reservation {} for user {} (Released seats: {})", 
                reservationId, userId, seats.stream().map(Seat::getSeatNumber).toList());

        return new com.example.seatreservation.dto.CancelReservationResponse(reservationId, com.example.seatreservation.entity.ReservationStatus.CANCELLED, "Reservation cancelled successfully");
    }

    private String computeRequestHash(UUID showId, List<String> sortedSeatNumbers) {
        try {
            String raw = showId.toString() + ":" + String.join(",", sortedSeatNumbers);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    private ReservationResponse mapToReservationResponse(Reservation reservation) {
        List<String> seatNumbers = reservation.getSeats().stream()
                .map(Seat::getSeatNumber)
                .sorted()
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUserId(),
                seatNumbers,
                reservation.getAmountPaise(),
                reservation.getStatus()
        );
    }
}
