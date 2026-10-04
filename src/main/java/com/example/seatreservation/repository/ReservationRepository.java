package com.example.seatreservation.repository;

import com.example.seatreservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Query("SELECT COUNT(s) FROM Reservation r JOIN r.seats s WHERE r.show.id = :showId AND r.userId = :userId AND r.status = com.example.seatreservation.entity.ReservationStatus.CONFIRMED")
    long countConfirmedSeatsByShowIdAndUserId(@Param("showId") UUID showId, @Param("userId") String userId);

    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:lockKey))", nativeQuery = true)
    void acquireAdvisoryLock(@Param("lockKey") String lockKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);
}
