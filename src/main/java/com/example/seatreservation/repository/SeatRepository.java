package com.example.seatreservation.repository;

import com.example.seatreservation.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowId(UUID showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber ASC")
    List<Seat> findByShowIdAndSeatNumberInOrderAscForUpdate(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
    );
}
