package com.example.seatreservation.service;

import com.example.seatreservation.dto.CreateShowRequest;
import com.example.seatreservation.dto.SeatDto;
import com.example.seatreservation.dto.ShowResponse;
import com.example.seatreservation.entity.Seat;
import com.example.seatreservation.entity.SeatStatus;
import com.example.seatreservation.entity.Show;
import com.example.seatreservation.exception.InvalidRequestException;
import com.example.seatreservation.exception.ShowNotFoundException;
import com.example.seatreservation.repository.SeatRepository;
import com.example.seatreservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new InvalidRequestException("Show name must not be empty");
        }
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new InvalidRequestException("Show must contain at least one seat");
        }
        if (request.getPricePaise() <= 0) {
            throw new InvalidRequestException("Price in paise must be positive");
        }

        // Check for duplicate seat numbers in creation payload
        Set<String> uniqueSeats = new HashSet<>();
        for (String seatNumber : request.getSeats()) {
            if (seatNumber == null || seatNumber.isBlank()) {
                throw new InvalidRequestException("Seat number must not be blank");
            }
            if (!uniqueSeats.add(seatNumber.trim())) {
                throw new InvalidRequestException("Duplicate seat number '" + seatNumber + "' in show creation request");
            }
        }

        Show show = new Show(request.getName().trim(), request.getPricePaise(), request.getSeats().size());

        for (String seatNumber : request.getSeats()) {
            Seat seat = new Seat(show, seatNumber.trim(), SeatStatus.AVAILABLE);
            show.addSeat(seat);
        }

        Show savedShow = showRepository.save(show);
        return mapToShowResponse(savedShow);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShowState(UUID showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException(showId));

        List<Seat> seats = seatRepository.findByShowId(showId);

        long availableCount = seats.stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        long confirmedCount = seats.stream().filter(s -> s.getStatus() == SeatStatus.CONFIRMED).count();

        List<SeatDto> seatDtos = seats.stream()
                .map(s -> new SeatDto(s.getSeatNumber(), s.getStatus()))
                .toList();

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getTotalSeats(),
                availableCount,
                confirmedCount,
                seatDtos
        );
    }

    private ShowResponse mapToShowResponse(Show show) {
        List<SeatDto> seatDtos = show.getSeats().stream()
                .map(s -> new SeatDto(s.getSeatNumber(), s.getStatus()))
                .toList();

        long availableCount = show.getSeats().stream().filter(s -> s.getStatus() == SeatStatus.AVAILABLE).count();
        long confirmedCount = show.getSeats().stream().filter(s -> s.getStatus() == SeatStatus.CONFIRMED).count();

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getTotalSeats(),
                availableCount,
                confirmedCount,
                seatDtos
        );
    }
}
